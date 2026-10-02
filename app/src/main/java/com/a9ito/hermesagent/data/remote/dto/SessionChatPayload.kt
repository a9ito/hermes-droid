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
 * Builds the POST /api/sessions/{id}/chat[/stream] request body. Pure and
 * unit-tested (SessionChatPayloadTest).
 *
 * The server's ``_normalize_multimodal_content`` accepts ``message`` as EITHER a
 * plain string (text-only, the common case) or a list of ``{"type":"text"}`` /
 * ``{"type":"image_url"}`` parts (native OpenAI vision shape). We emit a plain
 * string when there are no attachments so trajectory logging and prompt caching
 * see the native shape, and switch to the parts array only when images are
 * present — matching exactly what the endpoint validates.
 */
object SessionChatPayload {

    fun build(message: String, attachments: List<ChatAttachment>, model: String?, modelOptions: JsonObject? = null): JsonElement =
        buildJsonObject {
            if (attachments.isEmpty()) {
                put("message", message)
            } else {
                putJsonArray("message") {
                    // A leading text part carries the prompt; skipped when the user
                    // sent images with no caption (server tolerates parts-only).
                    if (message.isNotEmpty()) {
                        addJsonObject {
                            put("type", "text")
                            put("text", message)
                        }
                    }
                    attachments.filter { it.isImage }.forEach { att ->
                        addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") { put("url", att.toDataUrl()) }
                        }
                    }
                }
            }
            if (model != null) put("model", model)
            // Per-turn reasoning/speed controls; omitted entirely on a default turn.
            modelOptions?.let { put("model_options", it) }
        }

    fun encode(json: Json, message: String, attachments: List<ChatAttachment>, model: String?, modelOptions: JsonObject? = null): String =
        json.encodeToString(JsonElement.serializer(), build(message, attachments, model, modelOptions))
}
