package com.a9ito.hermesagent.ui.runs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.a9ito.hermesagent.core.AgentRun
import com.a9ito.hermesagent.core.Capabilities
import com.a9ito.hermesagent.core.ConnectionConfig
import com.a9ito.hermesagent.core.ErrorKind
import com.a9ito.hermesagent.core.ModelOptions
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

/** One line in the live run transcript (tool activity, mid-turn commentary). */
data class RunLogLine(val kind: Kind, val text: String) {
    enum class Kind { TOOL_START, TOOL_DONE, TOOL_ERROR, INTERIM }
}

data class RunsUiState(
    val configured: Boolean = false,
    val configLoaded: Boolean = false,
    /** Null until capabilities resolve; drives the "not supported" gate. */
    val capabilities: Capabilities? = null,
    /**
     * Set when the capabilities probe itself FAILED (auth/network/transient),
     * as opposed to the gateway answering that it lacks run control. Kept
     * separate so a failed check offers "retry" instead of the misleading
     * "update your gateway" message. Null once capabilities resolve.
     */
    val capabilitiesError: ErrorKind? = null,
    val input: String = "",
    val run: AgentRun? = null,
    val submitting: Boolean = false,
    /** Live-streamed assistant answer, accumulated from message.delta. */
    val answer: String = "",
    /**
     * Reasoning text, accumulated across reasoning.available events into one
     * buffer and shown in a collapsible panel — the same treatment the session
     * chat gives a turn's thinking, kept separate from the tool-activity [log].
     */
    val reasoning: String = "",
    val log: List<RunLogLine> = emptyList(),
    val approval: RunApproval? = null,
    val errorKind: ErrorKind? = null,
    /** Model picked for the next run; null = instance default alias. */
    val model: String? = null,
    /** Flat /v1/models id list, fallback when model_options is unavailable. */
    val availableModels: List<String> = emptyList(),
    /** Rich provider catalog when the instance advertises model_options; null otherwise. */
    val modelOptions: ModelOptions? = null,
    /** True while a picker-triggered fresh /api/model/options fetch is in flight. */
    val refreshingModels: Boolean = false,
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
    /** Set once a Terminal event is applied, so a clean stream end skips the reconcile poll. */
    private var terminalApplied = false

    private companion object {
        /** Keep the live tool/commentary log bounded; a long run can emit thousands of lines. */
        const val MAX_LOG_LINES = 500
        /**
         * Clamp the accumulated answer/reasoning buffers. The per-line SSE cap
         * (SseLineReader, 16 MiB) bounds ONE frame; this bounds the SUM so a long
         * or hostile stream cannot grow the buffer without limit and OOM the app.
         */
        const val MAX_STREAM_TEXT = 2 * 1024 * 1024 // 2 MB of accumulated text
    }

    init {
        viewModelScope.launch {
            repository.connectionFlow.collect { c ->
                val firstConfigured = c.isComplete && !config.isComplete
                config = c
                _state.update { it.copy(configured = c.isComplete, configLoaded = true) }
                if (firstConfigured) {
                    loadCapabilities()
                    loadModelCatalog()
                }
            }
        }
    }

    fun onInputChange(value: String) = _state.update { it.copy(input = value) }

    private fun loadCapabilities() {
        if (!config.isComplete) return
        _state.update { it.copy(capabilitiesError = null) }
        viewModelScope.launch {
            when (val res = repository.fetchCapabilities(config)) {
                is ApiResult.Success -> _state.update { it.copy(capabilities = res.data, capabilitiesError = null) }
                // The probe FAILED (auth/network/transient). Do NOT fall back to a
                // run-less baseline here: that renders the misleading "update your
                // gateway" screen for what is really a connection problem. Leave
                // capabilities null and record the error so the screen can offer retry.
                is ApiResult.Failure -> _state.update { it.copy(capabilitiesError = res.kind) }
            }
        }
    }

    /** Re-probe capabilities after a failed check (user tapped retry). */
    fun retryCapabilities() = loadCapabilities()

    /**
     * Load the model catalog for the picker. Prefer the rich /api/model/options
     * catalog; fall back to the flat /v1/models id list. Failures are non-fatal —
     * the picker just stays hidden — so they never touch errorKind.
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
     * pricing/entitlement synchronously. Called when the picker opens, so the
     * round-trip only happens on demand. Mirrors the chat/session treatment.
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

    /** Pick the model for the next run (local to this screen). */
    fun selectModel(model: String) = _state.update { it.copy(model = model) }

    fun submit() {
        val text = _state.value.input.trim()
        if (!config.isComplete || text.isEmpty() || _state.value.isActive) return
        _state.update {
            it.copy(submitting = true, errorKind = null, answer = "", reasoning = "", log = emptyList(), approval = null)
        }
        terminalApplied = false
        viewModelScope.launch {
            when (val res = repository.createRun(config, text, model = _state.value.model)) {
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
            // Only poll to reconcile when the stream ended WITHOUT a terminal event
            // (dropped/keepalive gap). A clean terminal close already settled the UI,
            // so the extra getRun() would be a redundant request on every completion.
            if (!terminalApplied) reconcile(runId)
        }
    }

    private fun apply(ev: RunStreamEvent) {
        when (ev) {
            is RunStreamEvent.Delta -> _state.update { it.copy(answer = clampText(it.answer + ev.text)) }
            is RunStreamEvent.Interim -> addLog(RunLogLine.Kind.INTERIM, ev.text)
            // Accumulate reasoning into one buffer for the collapsible panel, mirroring
            // the session chat's per-turn thinking (not interleaved into the tool log).
            // Each reasoning.available carries one iteration's thinking (server-capped),
            // so separate distinct emissions with a blank line instead of mashing them.
            is RunStreamEvent.Reasoning ->
                if (ev.text.isNotBlank()) _state.update {
                    val sep = if (it.reasoning.isEmpty()) "" else "\n\n"
                    it.copy(reasoning = clampText(it.reasoning + sep + ev.text.trim()))
                }
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
            is RunStreamEvent.Terminal -> {
                terminalApplied = true
                _state.update {
                    it.copy(
                        run = it.run?.copy(
                            status = AgentRun.Status.fromWire(ev.status),
                            output = ev.output ?: it.run.output,
                            error = ev.error ?: it.run.error,
                        ),
                        answer = ev.output?.takeIf { o -> o.isNotEmpty() }?.let(::clampText) ?: it.answer,
                        approval = null,
                    )
                }
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
        _state.update {
            // Bound the live log: keep the most recent MAX_LOG_LINES so a long run
            // (thousands of tool frames) can't grow the list without limit.
            val next = it.log + RunLogLine(kind, text.trim())
            it.copy(log = if (next.size > MAX_LOG_LINES) next.takeLast(MAX_LOG_LINES) else next)
        }
    }

    /** Clamp an accumulated stream buffer to [MAX_STREAM_TEXT], keeping the tail (newest). */
    private fun clampText(text: String): String =
        if (text.length > MAX_STREAM_TEXT) text.takeLast(MAX_STREAM_TEXT) else text

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
