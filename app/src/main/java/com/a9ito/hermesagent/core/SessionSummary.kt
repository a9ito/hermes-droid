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
    val preview: String? = null,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val isFork: Boolean = false,
)

/** One message in a session transcript, reduced to what the UI renders. */
data class SessionMessage(
    val id: Long,
    val role: Role,
    val text: String,
    val toolName: String? = null,
) {
    enum class Role { USER, ASSISTANT, TOOL, SYSTEM, OTHER }
}
