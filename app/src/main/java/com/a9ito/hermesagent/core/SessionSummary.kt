package com.a9ito.hermesagent.core

/**
 * Domain view of one persisted Hermes session, mapped from SessionDto. Pure /
 * Android-free so it can be built and asserted in host unit tests.
 */
data class SessionSummary(
    val id: String,
    val title: String,
    val model: String? = null,
    val messageCount: Int = 0,
    val lastActive: Double? = null,
    /** Epoch seconds the session was created, for a session-age readout. */
    val startedAt: Double? = null,
    /** Cumulative input+output tokens billed across the session's whole life. */
    val totalTokens: Long = 0,
    val preview: String? = null,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val isFork: Boolean = false,
    /** Origin surface that created the session (e.g. "cli", "discord", "api_server"). */
    val source: String? = null,
)

/** One message in a session transcript, reduced to what the UI renders. */
data class SessionMessage(
    val id: Long,
    val role: Role,
    val text: String,
    val toolName: String? = null,
    /** Assistant reasoning/thinking captured for this turn, if any. */
    val reasoning: String? = null,
) {
    enum class Role { USER, ASSISTANT, TOOL, SYSTEM, OTHER }
}
