package com.a9ito.hermesagent.data.remote.dto

import com.a9ito.hermesagent.core.ChatAttachment
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Builds the POST /v1/chat/completions request body. Pure and unit-tested
 * (ChatCompletionPayloadTest), replacing the former typed ChatCompletionRequest
 * so the message content can be EITHER a plain string or the OpenAI multimodal
 * parts array — the server (and real OpenAI) accept both.
 *
 * Only the LAST message carries inline images, because that is the user turn
 * just sent; every prior turn stays a plain-string content so trajectory logging
 * and prompt caching see the native text shape. [provider] is a Hermes extension
 * sent alongside [model] so the switch is honored even when the instance leaves
 * `direct_model_requests` off (a real OpenAI endpoint ignores the extra field).
 */
object ChatCompletionPayload {

    const val DEFAULT_MODEL = "hermes-agent"

    fun build(
        model: String?,
        messages: List<ChatMessageDto>,
        stream: Boolean,
        provider: String?,
        attachments: List<ChatAttachment> = emptyList(),
        modelOptions: JsonObject? = null,
    ): JsonElement = buildJsonObject {
        put("model", model?.takeIf { it.isNotBlank() } ?: DEFAULT_MODEL)
        val images = attachments.filter { it.isImage }
        val lastIndex = messages.lastIndex
        putJsonArray("messages") {
            messages.forEachIndexed { i, m ->
                addJsonObject {
                    put("role", m.role)
                    // Attach images only to the final user turn, as a parts array;
                    // everything else stays a plain-string content.
                    if (i == lastIndex && m.role == "user" && images.isNotEmpty()) {
                        putJsonArray("content") {
                            if (m.content.isNotEmpty()) {
                                addJsonObject {
                                    put("type", "text")
                                    put("text", m.content)
                                }
                            }
                            images.forEach { att ->
                                addJsonObject {
                                    put("type", "image_url")
                                    putJsonObject("image_url") { put("url", att.toDataUrl()) }
                                }
                            }
                        }
                    } else {
                        put("content", m.content)
                    }
                }
            }
        }
        put("stream", stream)
        provider?.takeIf { it.isNotBlank() }?.let { put("provider", it) }
        // Per-turn reasoning/speed controls; omitted entirely on a default turn.
        modelOptions?.let { put("model_options", it) }
    }

    fun encode(
        json: Json,
        model: String?,
        messages: List<ChatMessageDto>,
        stream: Boolean,
        provider: String?,
        attachments: List<ChatAttachment> = emptyList(),
        modelOptions: JsonObject? = null,
    ): String = json.encodeToString(
        JsonElement.serializer(),
        build(model, messages, stream, provider, attachments, modelOptions),
    )
}
