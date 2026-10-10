package com.a9ito.hermesagent.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * GET /health/detailed — authenticated readiness/status payload. Confirmed
 * against api_server.py::_handle_health_detailed. Everything is nullable because
 * the surface reports bounded status and older gateways omit fields.
 */
@Serializable
data class HealthDetailedDto(
    val status: String? = null,
    val platform: String? = null,
    val version: String? = null,
    @SerialName("gateway_state") val gatewayState: String? = null,
    @SerialName("active_agents") val activeAgents: Int? = null,
    @SerialName("gateway_busy") val gatewayBusy: Boolean? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    // Map of platform-name -> arbitrary state object; kept as raw JSON so we
    // don't over-fit a schema the gateway may extend.
    val platforms: Map<String, JsonElement>? = null,
    val readiness: ReadinessDto? = null,
)

@Serializable
data class ReadinessDto(
    val status: String? = null,
    val checks: ReadinessChecksDto? = null,
)

/** Subset of readiness.checks the app reads — only the live background queues. */
@Serializable
data class ReadinessChecksDto(
    @SerialName("background_queues") val backgroundQueues: BackgroundQueuesDto? = null,
)

/**
 * readiness.checks.background_queues — live gateway-wide work counters.
 * ``active_delegations`` is the number of subagents running across the whole
 * gateway (not scoped to one session); ``active_api_runs`` are durable runs in
 * flight. Confirmed against api_server.py health payload.
 */
@Serializable
data class BackgroundQueuesDto(
    @SerialName("active_api_runs") val activeApiRuns: Int? = null,
    @SerialName("active_delegations") val activeDelegations: Int? = null,
)

/** GET /v1/models — OpenAI-compatible model list. */
@Serializable
data class ModelsResponse(
    val data: List<ModelDto> = emptyList(),
) {
    fun primaryModelId(): String? = data.firstOrNull()?.id
}

@Serializable
data class ModelDto(
    val id: String,
    @SerialName("owned_by") val ownedBy: String? = null,
)
