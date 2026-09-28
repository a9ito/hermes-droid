package com.a9ito.hermesagent.core

/**
 * Pure state reducer for the chat screen's in-memory history. No Android or
 * coroutine types so it is fully unit-testable. The ViewModel owns a
 * [ChatHistory] and calls these transitions; Compose renders [messages].
 */
data class ChatHistory(
    val messages: List<ChatMessage> = emptyList(),
    private val nextId: Long = 1,
) {
    /** Append a user turn plus an empty streaming assistant placeholder. */
    fun startTurn(userText: String, attachmentCount: Int = 0): Pair<ChatHistory, Long> {
        val userMsg = ChatMessage(nextId, ChatMessage.Role.USER, userText, attachmentCount = attachmentCount)
        val assistantId = nextId + 1
        val assistantMsg = ChatMessage(assistantId, ChatMessage.Role.ASSISTANT, "", streaming = true)
        return ChatHistory(messages + userMsg + assistantMsg, nextId + 2) to assistantId
    }

    /** Append one already-complete message (used when replaying server history). */
    fun appendFinal(role: ChatMessage.Role, text: String): ChatHistory =
        ChatHistory(messages + ChatMessage(nextId, role, text), nextId + 1)

    /** Append a delta to the streaming assistant message [id]. */
    fun appendDelta(id: Long, delta: String): ChatHistory =
        copyMapping(id) { it.copy(text = it.text + delta) }

    /** Replace the assistant message [id] text wholesale (non-streaming path). */
    fun setText(id: Long, text: String): ChatHistory =
        copyMapping(id) { it.copy(text = text) }

    /** Mark the assistant message [id] as finished streaming. */
    fun finish(id: Long): ChatHistory =
        copyMapping(id) { it.copy(streaming = false) }

    /** Mark the assistant message [id] as an error carrying [kind]. */
    fun fail(id: Long, kind: ErrorKind): ChatHistory =
        copyMapping(id) { it.copy(text = "", streaming = false, error = true, errorKind = kind) }

    fun clear(): ChatHistory = ChatHistory(emptyList(), nextId)

    private fun copyMapping(id: Long, transform: (ChatMessage) -> ChatMessage): ChatHistory =
        copy(messages = messages.map { if (it.id == id) transform(it) else it })
}
