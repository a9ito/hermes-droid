package com.a9ito.hermesagent.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OpenAI-compatible Chat Completions request for POST /v1/chat/completions.
 *
 * Contract confirmed against Hermes' own api_server.py + the API Server docs:
 * the endpoint is stateless (full history sent each call) and streaming is
 * opt-in via [stream]. Model defaults to the profile alias "hermes-agent".
 *
 * [provider] is a Hermes extension, not standard OpenAI. The stateless endpoint
 * honors a BARE model only when the instance sets `direct_model_requests: true`;
 * sending an explicit provider alongside the model switches reliably regardless
 * of that flag. It is omitted when null (serialized with explicitNulls=false),
 * and a real OpenAI endpoint ignores the extra field harmlessly.
 */
@Serializable
data class ChatCompletionRequest(
    val model: String = "hermes-agent",
    val messages: List<ChatMessageDto>,
    val stream: Boolean = false,
    val provider: String? = null,
)

@Serializable
data class ChatMessageDto(
    val role: String,
    val content: String,
)

// ---- Non-streaming response ----

@Serializable
data class ChatCompletionResponse(
    val id: String? = null,
    val choices: List<ChatChoice> = emptyList(),
) {
    /** First choice's assistant text, or empty. */
    fun firstText(): String = choices.firstOrNull()?.message?.content.orEmpty()
}

@Serializable
data class ChatChoice(
    val index: Int = 0,
    val message: ChatMessageDto? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

// ---- Streaming (SSE) chunk: data: {"choices":[{"delta":{"content":"..."}}]} ----

@Serializable
data class ChatCompletionChunk(
    val id: String? = null,
    val choices: List<ChatChunkChoice> = emptyList(),
)

@Serializable
data class ChatChunkChoice(
    val index: Int = 0,
    val delta: ChatDelta? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class ChatDelta(
    val role: String? = null,
    val content: String? = null,
)
