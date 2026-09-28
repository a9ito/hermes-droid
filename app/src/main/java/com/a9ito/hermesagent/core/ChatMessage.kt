package com.a9ito.hermesagent.core

/**
 * One chat turn kept in the session's in-memory history. Pure/Android-free so
 * the chat reducer can be unit-tested on the JVM.
 *
 * For an error turn, [error] is true and [errorKind] carries the classified
 * cause; the UI renders a localized string for it rather than showing [text].
 */
data class ChatMessage(
    val id: Long,
    val role: Role,
    val text: String,
    val streaming: Boolean = false,
    val error: Boolean = false,
    val errorKind: ErrorKind? = null,
    /** Number of images the user attached to this turn (display badge only). */
    val attachmentCount: Int = 0,
    /** Tool calls the agent ran during this (assistant) turn, in order. */
    val activities: List<ToolActivity> = emptyList(),
    /** True while the agent is reasoning and no answer text has arrived yet. */
    val thinking: Boolean = false,
    /** Mid-turn assistant commentary emitted beside tool calls (not yet in [text]). */
    val commentary: List<String> = emptyList(),
    /** Full reasoning/thinking text for a completed assistant turn (collapsible). */
    val reasoning: String? = null,
) {
    enum class Role { USER, ASSISTANT }
}

/**
 * One tool invocation surfaced live from the session chat stream
 * (tool.started/completed/failed frames). Pure/Android-free for host tests.
 */
data class ToolActivity(
    val toolName: String,
    val status: Status,
) {
    enum class Status { RUNNING, DONE, FAILED }
}
