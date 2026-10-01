package com.a9ito.hermesagent.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.a9ito.hermesagent.core.ChatHistory
import com.a9ito.hermesagent.core.ChatMessage
import com.a9ito.hermesagent.core.ConnectionConfig
import com.a9ito.hermesagent.core.ErrorKind
import com.a9ito.hermesagent.core.ModelOptions
import com.a9ito.hermesagent.data.ApiResult
import com.a9ito.hermesagent.data.HermesRepository
import com.a9ito.hermesagent.data.remote.dto.ChatMessageDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val configured: Boolean = false,
    val configLoaded: Boolean = false,
    val history: ChatHistory = ChatHistory(),
    val input: String = "",
    val sending: Boolean = false,
    val errorKind: ErrorKind? = null,
    /** Model picked for this quick-chat screen; null = instance default alias. */
    val model: String? = null,
    /** Flat /v1/models id list, fallback when model_options is unavailable. */
    val availableModels: List<String> = emptyList(),
    /** Rich provider catalog when the instance advertises model_options; null otherwise. */
    val modelOptions: ModelOptions? = null,
    /** True while a picker-triggered fresh /api/model/options fetch is in flight. */
    val refreshingModels: Boolean = false,
)

class ChatViewModel(
    private val repository: HermesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var config: ConnectionConfig = ConnectionConfig.EMPTY
    private var streamJob: Job? = null

    init {
        viewModelScope.launch {
            repository.connectionFlow.collect { c ->
                val firstConfigured = c.isComplete && !config.isComplete
                config = c
                _state.update { it.copy(configured = c.isComplete, configLoaded = true) }
                if (firstConfigured) loadModelCatalog()
            }
        }
    }

    /**
     * Load the model catalog for the picker. Prefer the rich /api/model/options
     * catalog; fall back to the flat /v1/models id list when the instance
     * doesn't advertise model_options. Failures are non-fatal — the picker just
     * stays hidden — so they never touch errorKind.
     */
    private fun loadModelCatalog() {
        if (!config.isComplete) return
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

    /**
     * Re-fetch the catalog with refresh=true so the server settles free-tier
     * pricing/entitlement synchronously (a Portal round-trip). The cheap load at
     * startup can report `pricing_pending`, which locks every free model; this
     * is what actually unlocks them. Called when the picker window opens, so the
     * ~15s wait only happens on demand.
     */
    fun refreshModelOptions() {
        if (!config.isComplete || _state.value.refreshingModels) return
        _state.update { it.copy(refreshingModels = true) }
        viewModelScope.launch {
            when (val opts = repository.fetchModelOptions(config, refresh = true)) {
                is ApiResult.Success -> _state.update { it.copy(modelOptions = opts.data, refreshingModels = false) }
                is ApiResult.Failure -> _state.update { it.copy(refreshingModels = false) }
            }
        }
    }

    /** Pick the model for subsequent quick-chat turns (local to this screen). */
    fun selectModel(model: String) = _state.update { it.copy(model = model) }

    fun onInputChange(value: String) = _state.update { it.copy(input = value) }
    fun consumeError() = _state.update { it.copy(errorKind = null) }

    /** Send the current input as a streaming turn. No-op if empty or busy. */
    fun send() {
        val text = _state.value.input.trim()
        if (text.isEmpty() || _state.value.sending) return
        if (!config.isComplete) {
            _state.update { it.copy(errorKind = ErrorKind.NO_CONNECTION) }
            return
        }

        val (newHistory, assistantId) = _state.value.history.startTurn(text)
        _state.update { it.copy(history = newHistory, input = "", sending = true, errorKind = null) }

        // Build the wire transcript from every non-error turn EXCEPT the empty
        // streaming placeholder we just added.
        val wire = newHistory.messages
            .filter { !(it.id == assistantId) && !it.error }
            .map { ChatMessageDto(role = if (it.role == ChatMessage.Role.USER) "user" else "assistant", content = it.text) }

        // A picked model is sent WITH its provider: the stateless completions
        // endpoint ignores a bare model unless the instance opts into
        // direct_model_requests, so pairing them makes the switch reliable.
        val model = _state.value.model
        val provider = model?.let { _state.value.modelOptions?.providerForModel(it) }

        streamJob = viewModelScope.launch {
            var received = false
            try {
                repository.streamChat(config, wire, model = model, provider = provider).collect { delta ->
                    received = true
                    _state.update { it.copy(history = it.history.appendDelta(assistantId, delta)) }
                }
                // Streaming produced nothing? Fall back to one non-streaming call.
                if (!received) {
                    when (val res = repository.sendChat(config, wire, model = model, provider = provider)) {
                        is ApiResult.Success ->
                            _state.update { it.copy(history = it.history.setText(assistantId, res.data)) }
                        is ApiResult.Failure ->
                            throw FallbackFailure(res.kind)
                    }
                }
                _state.update { it.copy(history = it.history.finish(assistantId), sending = false) }
            } catch (t: Throwable) {
                // Never turn coroutine cancellation (stop/clear/screen leave) into a
                // visible error; rethrow so structured concurrency keeps working.
                if (t is CancellationException) throw t
                val kind = if (t is FallbackFailure) t.kind else repository.classify(t)
                _state.update {
                    it.copy(
                        history = it.history.fail(assistantId, kind),
                        sending = false,
                        errorKind = kind,
                    )
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

    fun clearHistory() {
        streamJob?.cancel()
        streamJob = null
        _state.update { it.copy(history = it.history.clear(), sending = false) }
    }

    /** Carries a classified failure out of the non-streaming fallback branch. */
    private class FallbackFailure(val kind: ErrorKind) : Exception()

    class Factory(private val repository: HermesRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ChatViewModel(repository) as T
    }
}
