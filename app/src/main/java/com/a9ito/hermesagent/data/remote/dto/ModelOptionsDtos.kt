package com.a9ito.hermesagent.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * GET /api/model/options — the provider-catalog picker payload. Field names
 * confirmed against hermes_cli/inventory.py::build_models_payload +
 * build_model_options_payload: the top level is
 * {"providers": [...], "model": <current>, "provider": <current>}, and each
 * provider row carries slug/name/models plus optional capabilities/pricing/
 * featured_models/unavailable_models maps keyed by model id.
 *
 * Almost everything is nullable/defaulted: the server only emits keys that
 * apply (pricing warms in the background, capabilities may be absent, custom
 * providers omit featured lists), and older gateways emit fewer keys.
 */
@Serializable
data class ModelOptionsResponse(
    val providers: List<ModelProviderDto> = emptyList(),
    // Current model + provider live at the TOP level, not per row.
    val model: String? = null,
    val provider: String? = null,
)

@Serializable
data class ModelProviderDto(
    val slug: String? = null,
    val name: String? = null,
    @SerialName("is_current") val isCurrent: Boolean = false,
    val authenticated: Boolean = true,
    @SerialName("is_user_defined") val isUserDefined: Boolean = false,
    @SerialName("auth_type") val authType: String? = null,
    @SerialName("key_env") val keyEnv: String? = null,
    val warning: String? = null,
    @SerialName("free_tier") val freeTier: Boolean = false,
    val models: List<String> = emptyList(),
    @SerialName("total_models") val totalModels: Int = 0,
    @SerialName("featured_models") val featuredModels: List<String> = emptyList(),
    @SerialName("unavailable_models") val unavailableModels: List<String> = emptyList(),
    // Keyed by model id -> {fast, reasoning, can_disable_reasoning?}.
    val capabilities: Map<String, CapabilityEntryDto> = emptyMap(),
    // Keyed by model id -> {input, output, cache, free, discount_percent?}.
    val pricing: Map<String, PricingEntryDto> = emptyMap(),
    // Present but unused here; kept raw so decoding never fails on it.
    val aliases: JsonElement? = null,
)

@Serializable
data class CapabilityEntryDto(
    val fast: Boolean = false,
    val reasoning: Boolean = false,
    @SerialName("can_disable_reasoning") val canDisableReasoning: Boolean = false,
)

/**
 * Pricing is pre-formatted by the server (e.g. "$3.00", "free", or ""). Kept as
 * strings; the app never does currency math. ``free`` is a real bool.
 */
@Serializable
data class PricingEntryDto(
    val input: String? = null,
    val output: String? = null,
    val cache: String? = null,
    val free: Boolean = false,
    @SerialName("discount_percent") val discountPercent: Int? = null,
)
