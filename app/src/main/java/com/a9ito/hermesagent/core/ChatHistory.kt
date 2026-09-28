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

    /** Append a completed assistant turn carrying reasoning (server-history replay). */
    fun appendFinalWithReasoning(text: String, reasoning: String?): ChatHistory =
        ChatHistory(
            messages + ChatMessage(nextId, ChatMessage.Role.ASSISTANT, text, reasoning = reasoning?.takeIf { it.isNotBlank() }),
            nextId + 1,
        )

    /** Append a delta to the streaming assistant message [id]. */
    fun appendDelta(id: Long, delta: String): ChatHistory =
        copyMapping(id) { it.copy(text = it.text + delta, thinking = false) }

    /** Replace the assistant message [id] text wholesale (non-streaming path). */
    fun setText(id: Long, text: String): ChatHistory =
        copyMapping(id) { it.copy(text = text, thinking = false) }

    /** Mark the assistant message [id] as reasoning (no answer text yet). */
    fun setThinking(id: Long, thinking: Boolean): ChatHistory =
        copyMapping(id) { if (it.text.isEmpty()) it.copy(thinking = thinking) else it }

    /**
     * Record that tool [toolName] started on the assistant message [id]. Thinking
     * ends once a concrete tool is running. A repeated start for the same tool
     * that's already RUNNING is ignored (server may resend).
     */
    fun toolStarted(id: Long, toolName: String): ChatHistory = copyMapping(id) { m ->
        val already = m.activities.any { it.toolName == toolName && it.status == ToolActivity.Status.RUNNING }
        if (already) m.copy(thinking = false)
        else m.copy(activities = m.activities + ToolActivity(toolName, ToolActivity.Status.RUNNING), thinking = false)
    }

    /** Mark the most recent RUNNING activity for [toolName] as [status]. */
    fun toolFinished(id: Long, toolName: String, status: ToolActivity.Status): ChatHistory =
        copyMapping(id) { m ->
            val idx = m.activities.indexOfLast { it.toolName == toolName && it.status == ToolActivity.Status.RUNNING }
            if (idx < 0) m
            else m.copy(activities = m.activities.toMutableList().also { it[idx] = it[idx].copy(status = status) })
        }

    /** Append a mid-turn commentary line to the assistant message [id]. */
    fun appendCommentary(id: Long, text: String): ChatHistory =
        copyMapping(id) { it.copy(commentary = it.commentary + text, thinking = false) }

    /** Mark the assistant message [id] as finished streaming. */
    fun finish(id: Long): ChatHistory =
        copyMapping(id) { it.copy(streaming = false, thinking = false) }

    /** Mark the assistant message [id] as an error carrying [kind]. */
    fun fail(id: Long, kind: ErrorKind): ChatHistory =
        copyMapping(id) { it.copy(text = "", streaming = false, thinking = false, error = true, errorKind = kind) }

    fun clear(): ChatHistory = ChatHistory(emptyList(), nextId)

    private fun copyMapping(id: Long, transform: (ChatMessage) -> ChatMessage): ChatHistory =
        copy(messages = messages.map { if (it.id == id) transform(it) else it })
}
