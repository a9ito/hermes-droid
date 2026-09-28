package com.a9ito.hermesagent.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * GET /v1/capabilities — the instance's stable, machine-readable API surface.
 * Confirmed against api_server.py::_handle_capabilities. The app uses this to
 * adapt: only offer Runs control, Artifacts, or model-options when the connected
 * instance actually advertises them, and degrade gracefully against older
 * gateways that lack the endpoint entirely (see repository's 404 handling).
 *
 * ``features`` mixes booleans with objects/strings (e.g. runs_idempotency is an
 * object, session_continuity_header is a string), so only the booleans this app
 * gates on are declared; everything else is ignored via ignoreUnknownKeys.
 */
@Serializable
data class CapabilitiesDto(
    val `object`: String? = null,
    val platform: String? = null,
    val model: String? = null,
    val auth: AuthDto? = null,
    val runtime: RuntimeDto? = null,
    val features: FeaturesDto? = null,
    // name -> {method, path}. The most reliable "does this route exist" signal.
    val endpoints: Map<String, EndpointDto> = emptyMap(),
)

@Serializable
data class AuthDto(
    val type: String? = null,
    val required: Boolean = false,
)

@Serializable
data class RuntimeDto(
    val mode: String? = null,
    @SerialName("tool_execution") val toolExecution: String? = null,
    @SerialName("split_runtime") val splitRuntime: Boolean = false,
    val description: String? = null,
)

@Serializable
data class EndpointDto(
    val method: String? = null,
    val path: String? = null,
)

/** The subset of feature flags the app gates behavior on. All default false. */
@Serializable
data class FeaturesDto(
    @SerialName("run_submission") val runSubmission: Boolean = false,
    @SerialName("run_status") val runStatus: Boolean = false,
    @SerialName("run_events_sse") val runEventsSse: Boolean = false,
    @SerialName("run_stop") val runStop: Boolean = false,
    @SerialName("run_steer") val runSteer: Boolean = false,
    @SerialName("run_approval_response") val runApproval: Boolean = false,
    @SerialName("approval_events") val approvalEvents: Boolean = false,
    @SerialName("session_chat") val sessionChat: Boolean = false,
    @SerialName("session_chat_streaming") val sessionChatStreaming: Boolean = false,
    @SerialName("session_fork") val sessionFork: Boolean = false,
    @SerialName("session_model_lock") val sessionModelLock: Boolean = false,
    @SerialName("model_options") val modelOptions: Boolean = false,
    @SerialName("skills_api") val skillsApi: Boolean = false,
    @SerialName("reasoning_streaming") val reasoningStreaming: Boolean = false,
    @SerialName("audio_api") val audioApi: Boolean = false,
    @SerialName("realtime_voice") val realtimeVoice: Boolean = false,
    // Present but kept raw; some are objects (runs_idempotency, browser_extension_control).
    @SerialName("browser_extension_control") val browserExtensionControl: JsonElement? = null,
)
