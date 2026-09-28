package com.a9ito.hermesagent.data.remote.dto

import com.a9ito.hermesagent.core.ModelOption
import com.a9ito.hermesagent.core.ModelOptions
import com.a9ito.hermesagent.core.ModelPricing
import com.a9ito.hermesagent.core.ModelProvider

/**
 * Pure DTO -> domain mapper for /api/model/options. Unit-tested
 * (ModelOptionsMapperTest). The server puts current model/provider at the top
 * level and per-model capability/pricing/unavailable data in maps keyed by
 * model id, so this flattens each provider's model-id list into [ModelOption]s
 * with their hints attached.
 */
fun ModelOptionsResponse.toDomain(): ModelOptions {
    val currentModel = model?.takeIf { it.isNotBlank() }
    val currentProvider = provider?.takeIf { it.isNotBlank() }
    return ModelOptions(
        currentProvider = currentProvider,
        currentModel = currentModel,
        providers = providers.map { it.toDomain(currentModel) },
    )
}

private fun ModelProviderDto.toDomain(currentModel: String?): ModelProvider {
    val slugValue = slug ?: ""
    val unavailable = unavailableModels.toSet()
    val featured = featuredModels.toSet()
    val options = models.map { id ->
        val cap = capabilities[id]
        val price = pricing[id]
        ModelOption(
            id = id,
            isCurrent = id == currentModel,
            supportsReasoning = cap?.reasoning ?: false,
            canDisableReasoning = cap?.canDisableReasoning ?: false,
            supportsFastMode = cap?.fast ?: false,
            unavailable = id in unavailable,
            pricing = price?.toDomain(),
        )
    }
    // A provider that needs a key but isn't authenticated is shown greyed-out with a hint,
    // rather than hidden — so the user understands why a model they expect is missing.
    val needsAuth = !authenticated && authType != "local"
    return ModelProvider(
        slug = slugValue,
        name = name ?: slugValue,
        isCurrent = isCurrent,
        authenticated = authenticated,
        isUserDefined = isUserDefined,
        needsAuth = needsAuth,
        warning = warning,
        freeTier = freeTier,
        models = options,
        featuredModelIds = featured,
    )
}

private fun PricingEntryDto.toDomain(): ModelPricing = ModelPricing(
    input = input?.takeIf { it.isNotBlank() },
    output = output?.takeIf { it.isNotBlank() },
    cache = cache?.takeIf { it.isNotBlank() },
    free = free,
    discountPercent = discountPercent,
)
