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
) {
    enum class Role { USER, ASSISTANT }
}
