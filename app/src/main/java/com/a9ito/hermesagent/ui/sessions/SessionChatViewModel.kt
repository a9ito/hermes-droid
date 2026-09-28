package com.a9ito.hermesagent.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.a9ito.hermesagent.core.ChatHistory
import com.a9ito.hermesagent.core.ChatAttachment
import com.a9ito.hermesagent.core.ChatMessage
import com.a9ito.hermesagent.core.ConnectionConfig
import com.a9ito.hermesagent.core.ErrorKind
import com.a9ito.hermesagent.core.ModelOptions
import com.a9ito.hermesagent.core.SessionMessage
import com.a9ito.hermesagent.core.ToolActivity
import com.a9ito.hermesagent.data.ApiResult
import com.a9ito.hermesagent.data.HermesRepository
import com.a9ito.hermesagent.data.remote.SessionStreamEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionChatUiState(
    val sessionId: String,
    val configured: Boolean = false,
    val loadingHistory: Boolean = false,
    val history: ChatHistory = ChatHistory(),
    val input: String = "",
    /** Images staged for the next turn (cleared on send). */
    val pendingAttachments: List<ChatAttachment> = emptyList(),
    val sending: Boolean = false,
    val errorKind: ErrorKind? = null,
    /** Model currently locked for this session, if any (shown in the app bar). */
    val model: String? = null,
    /** Model ids offered by the instance, for the per-session picker (flat fallback). */
    val availableModels: List<String> = emptyList(),
    /** Rich provider catalog when the instance advertises model_options; null otherwise. */
    val modelOptions: ModelOptions? = null,
)

/**
 * Chat against ONE persisted server session. Unlike the legacy [ChatViewModel],
 * this loads existing history from the server and streams each turn through
 * /api/sessions/{id}/chat/stream, so the transcript is durable and shared with
 * every other Hermes surface (CLI, Discord, desktop).
 */
class SessionChatViewModel(
    private val sessionId: String,
    private val repository: HermesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SessionChatUiState(sessionId = sessionId))
    val state: StateFlow<SessionChatUiState> = _state.asStateFlow()

    private var config: ConnectionConfig = ConnectionConfig.EMPTY
    private var streamJob: Job? = null

    init {
        viewModelScope.launch {
            repository.connectionFlow.collect { c ->
                val firstConfigured = c.isComplete && !config.isComplete
                config = c
                _state.update { it.copy(configured = c.isComplete) }
                if (firstConfigured) loadHistory()
            }
        }
    }

    private fun loadHistory() {
        if (!config.isComplete) return
        _state.update { it.copy(loadingHistory = true) }
        viewModelScope.launch {
            when (val res = repository.sessionMessages(config, sessionId)) {
                is ApiResult.Success ->
                    _state.update { it.copy(loadingHistory = false, history = res.data.toHistory()) }
                is ApiResult.Failure ->
                    _state.update { it.copy(loadingHistory = false, errorKind = res.kind) }
            }
        }
        // Session model + instance model list feed the per-session picker; failures
        // are non-fatal (picker just stays empty), so they don't touch errorKind.
        viewModelScope.launch {
            (repository.sessionDetail(config, sessionId) as? ApiResult.Success)?.let { res ->
                _state.update { it.copy(model = res.data.model) }
            }
        }
        // Prefer the rich /api/model/options catalog; fall back to the flat /v1/models
        // id list when the instance doesn't advertise model_options (older gateway
        // returns 404/500 -> we just keep the flat list).
        viewModelScope.launch {
            when (val opts = repository.fetchModelOptions(config)) {
                is ApiResult.Success -> _state.update { it.copy(modelOptions = opts.data) }
                is ApiResult.Failure -> Unit
            }
            if (_state.value.modelOptions?.isEmpty != false) {
                (repository.fetchModels(config) as? ApiResult.Success)?.let { res ->
                    _state.update { it.copy(availableModels = res.data) }
                }
            }
        }
    }

    /** Lock this session to [model] for subsequent turns. */
    fun selectModel(model: String) {
        if (!config.isComplete || model == _state.value.model) return
        viewModelScope.launch {
            when (val res = repository.lockSessionModel(config, sessionId, model)) {
                is ApiResult.Success -> _state.update { it.copy(model = model) }
                is ApiResult.Failure -> _state.update { it.copy(errorKind = res.kind) }
            }
        }
    }

    fun onInputChange(value: String) = _state.update { it.copy(input = value) }
    fun consumeError() = _state.update { it.copy(errorKind = null) }

    /** Stage an image for the next turn (dedup + cap enforced). */
    fun addAttachment(attachment: ChatAttachment) = _state.update {
        if (it.pendingAttachments.size >= ChatAttachment.MAX_PER_TURN) it
        else it.copy(pendingAttachments = it.pendingAttachments + attachment)
    }

    fun removeAttachment(index: Int) = _state.update {
        if (index !in it.pendingAttachments.indices) it
        else it.copy(pendingAttachments = it.pendingAttachments.filterIndexed { i, _ -> i != index })
    }

    fun send() {
        val text = _state.value.input.trim()
        val attachments = _state.value.pendingAttachments
        if ((text.isEmpty() && attachments.isEmpty()) || _state.value.sending) return
        if (!config.isComplete) {
            _state.update { it.copy(errorKind = ErrorKind.NO_CONNECTION) }
            return
        }
        val (newHistory, assistantId) = _state.value.history.startTurn(text, attachmentCount = attachments.size)
        _state.update {
            it.copy(history = newHistory, input = "", pendingAttachments = emptyList(), sending = true, errorKind = null)
        }

        streamJob = viewModelScope.launch {
            try {
                repository.streamSessionChat(config, sessionId, text, attachments).collect { event ->
                    when (event) {
                        is SessionStreamEvent.Delta ->
                            _state.update { it.copy(history = it.history.appendDelta(assistantId, event.text)) }
                        is SessionStreamEvent.Completed ->
                            // Only overwrite if we never received deltas (server may send both).
                            _state.update {
                                val current = it.history.messages.firstOrNull { m -> m.id == assistantId }?.text.orEmpty()
                                if (current.isEmpty()) it.copy(history = it.history.setText(assistantId, event.content)) else it
                            }
                        SessionStreamEvent.Thinking ->
                            _state.update { it.copy(history = it.history.setThinking(assistantId, true)) }
                        is SessionStreamEvent.ToolStarted ->
                            _state.update { it.copy(history = it.history.toolStarted(assistantId, event.toolName)) }
                        is SessionStreamEvent.ToolCompleted ->
                            _state.update { it.copy(history = it.history.toolFinished(assistantId, event.toolName, ToolActivity.Status.DONE)) }
                        is SessionStreamEvent.ToolFailed ->
                            _state.update { it.copy(history = it.history.toolFinished(assistantId, event.toolName, ToolActivity.Status.FAILED)) }
                        is SessionStreamEvent.Commentary ->
                            _state.update { it.copy(history = it.history.appendCommentary(assistantId, event.text)) }
                        is SessionStreamEvent.Failed ->
                            throw SessionStreamFailure(event.message)
                        SessionStreamEvent.Done, SessionStreamEvent.Ignored -> Unit
                    }
                }
                _state.update { it.copy(history = it.history.finish(assistantId), sending = false) }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                val kind = repository.classify(t)
                _state.update {
                    it.copy(history = it.history.fail(assistantId, kind), sending = false, errorKind = kind)
                }
            }
        }
    }

    fun stop() {
        streamJob?.cancel()
        streamJob = null
        _state.update { st ->
            val lastAssistant = st.history.messages.lastOrNull { it.role == ChatMessage.Role.ASSISTANT && it.streaming }
            val h = if (lastAssistant != null) st.history.finish(lastAssistant.id) else st.history
            st.copy(history = h, sending = false)
        }
    }

    /** A server-reported error frame carried out of the stream collector. */
    private class SessionStreamFailure(message: String) : Exception(message)

    class Factory(
        private val sessionId: String,
        private val repository: HermesRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SessionChatViewModel(sessionId, repository) as T
    }
}

/** Build the chat reducer's history from server messages (pure helper). */
private fun List<SessionMessage>.toHistory(): ChatHistory {
    var history = ChatHistory()
    for (m in this) {
        val role = if (m.role == SessionMessage.Role.USER) ChatMessage.Role.USER else ChatMessage.Role.ASSISTANT
        val text = if (m.role == SessionMessage.Role.TOOL && m.toolName != null) "[tool: ${m.toolName}]" else m.text
        if (text.isBlank()) continue
        history = history.appendFinal(role, text)
    }
    return history
}
