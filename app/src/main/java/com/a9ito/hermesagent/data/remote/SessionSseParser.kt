package com.a9ito.hermesagent.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * One decoded event from POST /api/sessions/{id}/chat/stream. Only the frames
 * the app renders are modeled; everything else maps to [Ignored]. The server's
 * SSE contract (api_server.py::_handle_session_chat_stream): each frame is an
 * ``event: <name>`` line followed by a ``data: {json}`` line; ``: keepalive``
 * comment lines and a final ``event: done`` close the stream.
 */
sealed interface SessionStreamEvent {
    /** Incremental assistant text (event: assistant.delta -> {"delta": "..."}). */
    data class Delta(val text: String) : SessionStreamEvent
    /** Terminal assistant text (event: assistant.completed -> {"content": "..."}). */
    data class Completed(val content: String) : SessionStreamEvent
    /** Server-reported failure (event: error -> {"message": "..."}). */
    data class Failed(val message: String) : SessionStreamEvent
    /** Stream finished (event: done, or data: [DONE]). */
    data object Done : SessionStreamEvent
    /** A frame we don't render (run.*, tool.progress, message.started, keepalive). */
    data object Ignored : SessionStreamEvent
}

/**
 * Stateful line-oriented parser for the session SSE stream. Feed it raw lines in
 * order; it remembers the most recent ``event:`` name and decodes the following
 * ``data:`` line against it. Pure (no Android/OkHttp), so it is unit-tested
 * directly (SessionSseParserTest).
 */
class SessionSseParser(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private var currentEvent: String? = null

    /** Returns the decoded event for this line, or null if the line yields nothing yet. */
    fun onLine(rawLine: String): SessionStreamEvent? {
        val line = rawLine.trimEnd('\r')
        return when {
            line.isEmpty() -> null                       // frame boundary
            line.startsWith(":") -> null                 // keep-alive comment
            line.startsWith("event:") -> {
                currentEvent = line.substring(6).trim()
                null
            }
            line.startsWith("data:") -> decodeData(line.substring(5).trim())
            else -> null
        }
    }

    private fun decodeData(payload: String): SessionStreamEvent {
        if (payload == "[DONE]") return SessionStreamEvent.Done
        val name = currentEvent
        val obj: JsonObject? = runCatching { json.parseToJsonElement(payload) as? JsonObject }.getOrNull()
        // Unparseable data for a real event must not crash or emit a phantom
        // empty delta — degrade to Ignored. Valid-but-empty ({}) still decodes.
        if (obj == null) return SessionStreamEvent.Ignored
        fun str(key: String): String = (obj[key] as? JsonPrimitive)?.contentOrNull ?: ""
        return when (name) {
            "assistant.delta" -> SessionStreamEvent.Delta(str("delta"))
            "assistant.completed" -> SessionStreamEvent.Completed(str("content"))
            "error" -> SessionStreamEvent.Failed(str("message"))
            "done" -> SessionStreamEvent.Done
            else -> SessionStreamEvent.Ignored
        }
    }
}
