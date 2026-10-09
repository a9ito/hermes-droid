package com.a9ito.hermesagent.data.remote

import com.a9ito.hermesagent.core.RunApproval
import com.a9ito.hermesagent.core.SafeText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * One decoded event from GET /v1/runs/{id}/events. Confirmed against
 * api_server_runs.py: each SSE frame is ``data: {json}`` where the JSON carries
 * an ``event`` field (message.delta, message.interim, tool.started,
 * tool.completed, reasoning.available, approval.request, subagent.*, and the
 * terminal run.<status>). ``: keepalive`` / ``: stream closed`` comment lines
 * carry no data. Only what the app renders is modeled; the rest -> [Ignored].
 */
sealed interface RunStreamEvent {
    /** Incremental assistant text (message.delta -> {"delta": "..."}). */
    data class Delta(val text: String) : RunStreamEvent
    /** Mid-turn assistant commentary (message.interim -> {"text": "..."}). */
    data class Interim(val text: String) : RunStreamEvent
    /** A tool began (tool.started -> {"tool": "...", "preview": "..."}). */
    data class ToolStarted(val tool: String, val preview: String?) : RunStreamEvent
    /** A tool finished (tool.completed -> {"tool", "duration", "error"}). */
    data class ToolCompleted(val tool: String, val isError: Boolean) : RunStreamEvent
    /** Reasoning text made available (reasoning.available -> {"text": "..."}). */
    data class Reasoning(val text: String) : RunStreamEvent
    /** A tool call needs approval (approval.request). */
    data class ApprovalRequest(val approval: RunApproval) : RunStreamEvent
    /** Terminal run event (run.completed / failed / cancelled / interrupted). */
    data class Terminal(val status: String, val output: String?, val error: String?) : RunStreamEvent
    /** Stream closed by the server sentinel. */
    data object Done : RunStreamEvent
    /** A frame we don't render. */
    data object Ignored : RunStreamEvent
}

/**
 * Stateful line parser for the run SSE stream. Unlike the session stream, the
 * event name rides INSIDE the JSON (``event`` field), not on a separate
 * ``event:`` line — so this parser keys off the payload. Pure (no Android/
 * OkHttp), unit-tested directly (RunSseParserTest).
 */
class RunSseParser(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun onLine(rawLine: String): RunStreamEvent? {
        val line = rawLine.trimEnd('\r')
        return when {
            line.isEmpty() -> null
            line.startsWith(":") -> null                  // keepalive / stream-closed comment
            line.startsWith("data:") -> decode(line.substring(5).trim())
            line.startsWith("event:") -> null             // run stream carries name in the body
            else -> null
        }
    }

    private fun decode(payload: String): RunStreamEvent {
        if (payload == "[DONE]") return RunStreamEvent.Done
        val obj: JsonObject = runCatching { json.parseToJsonElement(payload) as? JsonObject }
            .getOrNull() ?: return RunStreamEvent.Ignored
        fun str(key: String): String = (obj[key] as? JsonPrimitive)?.contentOrNull ?: ""
        fun strOrNull(key: String): String? = (obj[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }
        fun bool(key: String): Boolean = (obj[key] as? JsonPrimitive)?.booleanOrNull ?: false

        val name = str("event")
        return when {
            name == "message.delta" -> RunStreamEvent.Delta(str("delta"))
            name == "message.interim" -> RunStreamEvent.Interim(str("text"))
            name == "tool.started" -> RunStreamEvent.ToolStarted(SafeText.forControlDisplay(str("tool")) ?: "", SafeText.forControlDisplay(strOrNull("preview")))
            name == "tool.completed" -> RunStreamEvent.ToolCompleted(SafeText.forControlDisplay(str("tool")) ?: "", bool("error"))
            name == "reasoning.available" -> RunStreamEvent.Reasoning(str("text"))
            name == "approval.request" -> RunStreamEvent.ApprovalRequest(parseApproval(obj))
            name.startsWith("run.") -> {
                val status = name.removePrefix("run.")
                // Only the settled statuses are terminal; run.stopping/steered/etc. are not.
                val terminal = status in TERMINAL_STATUSES
                if (terminal) {
                    RunStreamEvent.Terminal(status, strOrNull("output"), strOrNull("error"))
                } else {
                    RunStreamEvent.Ignored
                }
            }
            else -> RunStreamEvent.Ignored
        }
    }

    private fun parseApproval(obj: JsonObject): RunApproval {
        val choices = (obj["choices"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?: listOf("once", "deny")
        fun s(key: String) = (obj[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }
        return RunApproval(
            choices = choices,
            // tool/command are shown at the human approval gate, so neutralize
            // bidi/zero-width/control chars that could disguise what is approved.
            tool = SafeText.forControlDisplay(s("tool") ?: s("tool_name")),
            command = SafeText.forControlDisplay(s("command")),
            requestId = s("request_id"),
        )
    }

    companion object {
        val TERMINAL_STATUSES = setOf("completed", "failed", "cancelled", "interrupted")
    }
}
