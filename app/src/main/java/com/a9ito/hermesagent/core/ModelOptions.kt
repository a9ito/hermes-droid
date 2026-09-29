package com.a9ito.hermesagent.core

/**
 * Domain view of GET /api/model/options — the configured provider catalog the
 * dashboard/TUI model picker uses. Pure / Android-free for host unit tests.
 *
 * This is the RICH picker source: unlike the flat /v1/models id list, it groups
 * models by provider, marks which provider/model is current, and carries
 * per-model capability + pricing hints. The app shows it when the instance
 * advertises the ``model_options`` capability and falls back to the flat list
 * otherwise.
 */
data class ModelOptions(
    val currentProvider: String? = null,
    val currentModel: String? = null,
    val providers: List<ModelProvider> = emptyList(),
) {
    val isEmpty: Boolean get() = providers.isEmpty()

    /**
     * A provider is still resolving free-tier pricing/entitlement on the server.
     * While true the server locks every model on that provider (fail-closed), so
     * the picker's unavailable flags are provisional — a refresh settles them.
     */
    val pending: Boolean get() = providers.any { it.pricingPending }

    /** All selectable model ids across authenticated providers, current first. */
    fun flatModelIds(): List<String> =
        providers.filter { it.authenticated }
            .flatMap { p -> p.models.map { it.id } }
            .distinct()
}

data class ModelProvider(
    val slug: String,
    val name: String,
    val isCurrent: Boolean = false,
    val authenticated: Boolean = true,
    val isUserDefined: Boolean = false,
    /** True when this provider requires credentials the instance doesn't have. */
    val needsAuth: Boolean = false,
    val warning: String? = null,
    val freeTier: Boolean = false,
    /** Server hasn't settled tier pricing/entitlement yet; availability is provisional. */
    val pricingPending: Boolean = false,
    val models: List<ModelOption> = emptyList(),
    /** Subset of [models] the server flags as headliners; empty for non-aggregators. */
    val featuredModelIds: Set<String> = emptySet(),
)

data class ModelOption(
    val id: String,
    val isCurrent: Boolean = false,
    val supportsReasoning: Boolean = false,
    val canDisableReasoning: Boolean = false,
    val supportsFastMode: Boolean = false,
    /** Server-unavailable under the current tier (e.g. paid model on free tier). */
    val unavailable: Boolean = false,
    val pricing: ModelPricing? = null,
) {
    val isFeaturedEligible: Boolean get() = !unavailable
}

/**
 * Pre-formatted price strings straight from the server (e.g. "$3.00", "free").
 * Kept as strings because the server already formats per-Mtok display values;
 * the app never does currency math.
 */
data class ModelPricing(
    val input: String? = null,
    val output: String? = null,
    val cache: String? = null,
    val free: Boolean = false,
    val discountPercent: Int? = null,
)
