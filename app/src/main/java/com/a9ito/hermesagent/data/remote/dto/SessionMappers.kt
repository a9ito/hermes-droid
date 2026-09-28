package com.a9ito.hermesagent.data.remote.dto

import com.a9ito.hermesagent.core.SessionMessage
import com.a9ito.hermesagent.core.SessionSummary
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Pure DTO -> domain mappers. No Android or coroutine types, so they are unit
 * tested directly on the JVM (SessionMapperTest).
 */

fun SessionDto.toSummary(): SessionSummary = SessionSummary(
    id = id,
    // A session may have no title yet; fall back to a short id so the row is never blank.
    title = title?.takeIf { it.isNotBlank() } ?: "(untitled ${id.take(8)})",
    model = model,
    messageCount = messageCount,
    lastActive = lastActive ?: endedAt ?: startedAt,
    preview = preview?.takeIf { it.isNotBlank() },
    pinned = pinned,
    archived = archived,
    isFork = parentSessionId != null,
)

private fun roleOf(raw: String?): SessionMessage.Role = when (raw?.lowercase()) {
    "user" -> SessionMessage.Role.USER
    "assistant" -> SessionMessage.Role.ASSISTANT
    "tool" -> SessionMessage.Role.TOOL
    "system" -> SessionMessage.Role.SYSTEM
    else -> SessionMessage.Role.OTHER
}

/**
 * Extract human-readable text from a message's ``content``, which the server may
 * send as a bare string OR an OpenAI-style multimodal array of parts
 * (``[{"type":"text","text":"..."}, {"type":"image_url", ...}]``). Non-text
 * parts are summarized rather than dropped silently.
 */
fun extractMessageText(content: JsonElement?): String = when (content) {
    null -> ""
    is JsonPrimitive -> content.contentOrNull ?: ""
    is JsonArray -> content.joinToString("\n") { part ->
        when (part) {
            is JsonPrimitive -> part.contentOrNull ?: ""
            is JsonObject -> {
                val type = (part["type"] as? JsonPrimitive)?.contentOrNull
                when (type) {
                    "text", null -> (part["text"] as? JsonPrimitive)?.contentOrNull ?: ""
                    "image_url", "image" -> "[image]"
                    else -> "[$type]"
                }
            }
            else -> ""
        }
    }.trim()
    is JsonObject -> (content["text"] as? JsonPrimitive)?.contentOrNull ?: ""
}

fun SessionMessageDto.toDomain(): SessionMessage = SessionMessage(
    id = id ?: 0L,
    role = roleOf(role),
    text = extractMessageText(content),
    toolName = toolName,
    // reasoning and reasoning_content are the same text on this server; prefer the
    // shorter key, fall back to the other so a future divergence doesn't drop it.
    reasoning = reasoning?.takeIf { it.isNotBlank() } ?: reasoningContent?.takeIf { it.isNotBlank() },
)

/** Drop empty/system rows so the transcript shows only meaningful turns. */
fun List<SessionMessageDto>.toDisplayMessages(): List<SessionMessage> =
    map { it.toDomain() }
        .filter { it.role != SessionMessage.Role.SYSTEM }
        .filter { it.text.isNotBlank() || it.toolName != null || !it.reasoning.isNullOrBlank() }
