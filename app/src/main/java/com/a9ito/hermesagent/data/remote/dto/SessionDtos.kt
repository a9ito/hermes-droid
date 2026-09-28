package com.a9ito.hermesagent.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * DTOs for the /api/sessions resource. Field set and names confirmed against
 * api_server.py::_session_response and ::_message_response. Everything is
 * nullable / defaulted because the server only emits keys that are present and
 * older gateways omit some.
 */
@Serializable
data class SessionDto(
    val id: String,
    val source: String? = null,
    val model: String? = null,
    val title: String? = null,
    @SerialName("started_at") val startedAt: Double? = null,
    @SerialName("ended_at") val endedAt: Double? = null,
    @SerialName("end_reason") val endReason: String? = null,
    @SerialName("message_count") val messageCount: Int = 0,
    @SerialName("tool_call_count") val toolCallCount: Int = 0,
    @SerialName("input_tokens") val inputTokens: Long = 0,
    @SerialName("output_tokens") val outputTokens: Long = 0,
    @SerialName("estimated_cost_usd") val estimatedCostUsd: Double? = null,
    @SerialName("actual_cost_usd") val actualCostUsd: Double? = null,
    @SerialName("parent_session_id") val parentSessionId: String? = null,
    @SerialName("last_active") val lastActive: Double? = null,
    val preview: String? = null,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val hidden: Boolean = false,
)

@Serializable
data class SessionListResponse(
    val data: List<SessionDto> = emptyList(),
    val limit: Int = 0,
    val offset: Int = 0,
    @SerialName("has_more") val hasMore: Boolean = false,
)

/** Wrapper for create / get / patch / fork, which return {"session": {...}}. */
@Serializable
data class SessionEnvelope(
    val session: SessionDto? = null,
)

@Serializable
data class CreateSessionRequest(
    val title: String? = null,
    val model: String? = null,
    @SerialName("system_prompt") val systemPrompt: String? = null,
)

@Serializable
data class ForkSessionRequest(
    val title: String? = null,
)

@Serializable
data class PatchSessionRequest(
    val title: String? = null,
    val pinned: Boolean? = null,
    val archived: Boolean? = null,
    val hidden: Boolean? = null,
)

@Serializable
data class DeleteSessionResponse(
    val id: String? = null,
    val deleted: Boolean = false,
)

/**
 * One persisted message. ``content`` is kept as a raw [JsonElement] because the
 * server may send a plain string (user/assistant text) or a multimodal array;
 * the mapper extracts display text safely rather than failing the whole parse.
 */
@Serializable
data class SessionMessageDto(
    val id: Long? = null,
    @SerialName("session_id") val sessionId: String? = null,
    val role: String? = null,
    val content: JsonElement? = null,
    @SerialName("tool_name") val toolName: String? = null,
    val timestamp: Double? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
    @SerialName("display_kind") val displayKind: String? = null,
)

@Serializable
data class SessionMessagesResponse(
    @SerialName("session_id") val sessionId: String? = null,
    val data: List<SessionMessageDto> = emptyList(),
)

/** POST /api/sessions/{id}/model — acknowledge a model lock for the session. */
@Serializable
data class ModelLockRequest(
    val model: String,
    val provider: String? = null,
)

/** POST /api/sessions/{id}/chat[/stream] request body. */
@Serializable
data class SessionChatRequest(
    val message: String,
    val model: String? = null,
)
