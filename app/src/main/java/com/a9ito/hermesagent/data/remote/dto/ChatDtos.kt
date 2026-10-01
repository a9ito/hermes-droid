package com.a9ito.hermesagent.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response-side DTOs for POST /v1/chat/completions. The REQUEST body is built
 * dynamically by [ChatCompletionPayload] (not a fixed data class) so a user turn
 * can carry inline images as an OpenAI multimodal parts array; the endpoint is
 * stateless (full history sent each call) and streaming is opt-in.
 */
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
