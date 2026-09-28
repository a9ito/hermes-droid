package com.a9ito.hermesagent.ui.runs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.a9ito.hermesagent.core.AgentRun
import com.a9ito.hermesagent.core.Capabilities
import com.a9ito.hermesagent.core.ConnectionConfig
import com.a9ito.hermesagent.core.ErrorKind
import com.a9ito.hermesagent.core.RunApproval
import com.a9ito.hermesagent.data.ApiResult
import com.a9ito.hermesagent.data.HermesRepository
import com.a9ito.hermesagent.data.remote.RunStreamEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One line in the live run transcript (tool activity, reasoning, commentary). */
data class RunLogLine(val kind: Kind, val text: String) {
    enum class Kind { TOOL_START, TOOL_DONE, TOOL_ERROR, REASONING, INTERIM }
}

data class RunsUiState(
    val configured: Boolean = false,
    val configLoaded: Boolean = false,
    /** Null until capabilities resolve; drives the "not supported" gate. */
    val capabilities: Capabilities? = null,
    val input: String = "",
    val run: AgentRun? = null,
    val submitting: Boolean = false,
    /** Live-streamed assistant answer, accumulated from message.delta. */
    val answer: String = "",
    val log: List<RunLogLine> = emptyList(),
    val approval: RunApproval? = null,
    val errorKind: ErrorKind? = null,
) {
    val isActive: Boolean get() = run != null && run.status.let { !it.isTerminal }
    val canSteer: Boolean get() = run?.status?.canSteer == true
    val canStop: Boolean get() = run?.status?.canStop == true
}

/**
 * Drives durable agent runs: submit a prompt, watch the live event stream (tool
 * activity, streamed answer, approval prompts), and control the run (stop,
 * steer, approve). The screen is gated on [Capabilities.supportsRunControl];
 * this ViewModel still fetches capabilities so the gate has data.
 *
 * The event stream is the source of truth for progress; when it ends we poll
 * [HermesRepository.getRun] once to reconcile the final status (the stream can
 * close on a keepalive gap before the terminal event lands).
 */
class RunsViewModel(
    private val repository: HermesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RunsUiState())
    val state: StateFlow<RunsUiState> = _state.asStateFlow()

    private var config: ConnectionConfig = ConnectionConfig.EMPTY
    private var streamJob: Job? = null

    init {
        viewModelScope.launch {
            repository.connectionFlow.collect { c ->
                val firstConfigured = c.isComplete && !config.isComplete
                config = c
                _state.update { it.copy(configured = c.isComplete, configLoaded = true) }
                if (firstConfigured) loadCapabilities()
            }
        }
    }

    fun onInputChange(value: String) = _state.update { it.copy(input = value) }

    private fun loadCapabilities() {
        if (!config.isComplete) return
        viewModelScope.launch {
            when (val res = repository.fetchCapabilities(config)) {
                is ApiResult.Success -> _state.update { it.copy(capabilities = res.data) }
                is ApiResult.Failure -> _state.update { it.copy(capabilities = Capabilities.baseline()) }
            }
        }
    }

    fun submit() {
        val text = _state.value.input.trim()
        if (!config.isComplete || text.isEmpty() || _state.value.isActive) return
        _state.update {
            it.copy(submitting = true, errorKind = null, answer = "", log = emptyList(), approval = null)
        }
        viewModelScope.launch {
            when (val res = repository.createRun(config, text)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(submitting = false, input = "", run = res.data) }
                    watch(res.data.runId)
                }
                is ApiResult.Failure ->
                    _state.update { it.copy(submitting = false, errorKind = res.kind) }
            }
        }
    }

    private fun watch(runId: String) {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            try {
                repository.streamRunEvents(config, runId).collect { ev -> apply(ev) }
            } catch (_: Throwable) {
                // Stream dropped (keepalive gap, network). Reconcile via a poll below.
            }
            // Stream ended: poll once so a missed terminal event still settles the UI.
            reconcile(runId)
        }
    }

    private fun apply(ev: RunStreamEvent) {
        when (ev) {
            is RunStreamEvent.Delta -> _state.update { it.copy(answer = it.answer + ev.text) }
            is RunStreamEvent.Interim -> addLog(RunLogLine.Kind.INTERIM, ev.text)
            is RunStreamEvent.Reasoning -> addLog(RunLogLine.Kind.REASONING, ev.text)
            is RunStreamEvent.ToolStarted ->
                addLog(RunLogLine.Kind.TOOL_START, ev.preview?.let { "${ev.tool}: $it" } ?: ev.tool)
            is RunStreamEvent.ToolCompleted ->
                addLog(if (ev.isError) RunLogLine.Kind.TOOL_ERROR else RunLogLine.Kind.TOOL_DONE, ev.tool)
            is RunStreamEvent.ApprovalRequest ->
                _state.update {
                    it.copy(
                        approval = ev.approval,
                        run = it.run?.copy(status = AgentRun.Status.WAITING_FOR_APPROVAL),
                    )
                }
            is RunStreamEvent.Terminal -> _state.update {
                it.copy(
                    run = it.run?.copy(
                        status = AgentRun.Status.fromWire(ev.status),
                        output = ev.output ?: it.run.output,
                        error = ev.error ?: it.run.error,
                    ),
                    answer = ev.output?.takeIf { o -> o.isNotEmpty() } ?: it.answer,
                    approval = null,
                )
            }
            RunStreamEvent.Done, RunStreamEvent.Ignored -> Unit
        }
    }

    private suspend fun reconcile(runId: String) {
        when (val res = repository.getRun(config, runId)) {
            is ApiResult.Success -> _state.update {
                it.copy(
                    run = res.data,
                    answer = res.data.output?.takeIf { o -> o.isNotEmpty() } ?: it.answer,
                    approval = if (res.data.status.isApprovalPending) it.approval else null,
                )
            }
            is ApiResult.Failure -> Unit
        }
    }

    fun stop() {
        val runId = _state.value.run?.runId ?: return
        _state.update { it.copy(run = it.run?.copy(status = AgentRun.Status.STOPPING)) }
        viewModelScope.launch { repository.stopRun(config, runId) }
    }

    fun steer(text: String) {
        val runId = _state.value.run?.runId ?: return
        if (text.isBlank()) return
        viewModelScope.launch { repository.steerRun(config, runId, text) }
    }

    fun approve(choice: String) {
        val runId = _state.value.run?.runId ?: return
        val requestId = _state.value.approval?.requestId
        _state.update { it.copy(approval = null) }
        viewModelScope.launch { repository.approveRun(config, runId, choice, requestId) }
    }

    private fun addLog(kind: RunLogLine.Kind, text: String) {
        if (text.isBlank()) return
        _state.update { it.copy(log = it.log + RunLogLine(kind, text.trim())) }
    }

    override fun onCleared() {
        streamJob?.cancel()
        super.onCleared()
    }

    class Factory(private val repository: HermesRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RunsViewModel(repository) as T
    }
}
