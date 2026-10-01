package com.a9ito.hermesagent.ui.jobs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.a9ito.hermesagent.core.ConnectionConfig
import com.a9ito.hermesagent.core.CronJob
import com.a9ito.hermesagent.core.ErrorKind
import com.a9ito.hermesagent.data.ApiResult
import com.a9ito.hermesagent.data.HermesRepository
import com.a9ito.hermesagent.data.JobAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class JobsUiState(
    val configured: Boolean = false,
    val configLoaded: Boolean = false,
    val loading: Boolean = false,
    val jobs: List<CronJob> = emptyList(),
    val errorKind: ErrorKind? = null,
    /** A one-shot user-facing note (e.g. "run triggered"), consumed by the screen. */
    val notice: Notice? = null,
) {
    enum class Notice { RUN_TRIGGERED, ACTION_FAILED }
}

/**
 * Owns the cron-jobs list and its lifecycle actions (create, pause, resume,
 * run-now, delete) over /api/jobs. Mutations refresh the list so the displayed
 * state always matches the server rather than an optimistic guess.
 */
class JobsViewModel(
    private val repository: HermesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(JobsUiState())
    val state: StateFlow<JobsUiState> = _state.asStateFlow()

    private var config: ConnectionConfig = ConnectionConfig.EMPTY

    init {
        viewModelScope.launch {
            repository.connectionFlow.collect { c ->
                val firstConfigured = c.isComplete && !config.isComplete
                config = c
                _state.update { it.copy(configured = c.isComplete, configLoaded = true) }
                if (firstConfigured) refresh()
            }
        }
    }

    fun refresh() {
        if (!config.isComplete) {
            _state.update { it.copy(errorKind = ErrorKind.NO_CONNECTION, jobs = emptyList()) }
            return
        }
        _state.update { it.copy(loading = true, errorKind = null) }
        viewModelScope.launch {
            when (val res = repository.listJobs(config)) {
                is ApiResult.Success ->
                    _state.update { it.copy(loading = false, jobs = res.data, errorKind = null) }
                is ApiResult.Failure ->
                    _state.update { it.copy(loading = false, errorKind = res.kind) }
            }
        }
    }

    fun create(name: String, schedule: String, prompt: String) {
        if (!config.isComplete || name.isBlank() || schedule.isBlank()) return
        viewModelScope.launch {
            when (val res = repository.createJob(config, name, schedule, prompt)) {
                is ApiResult.Success -> refresh()
                is ApiResult.Failure -> _state.update { it.copy(errorKind = res.kind) }
            }
        }
    }

    /**
     * Edit an existing job (PATCH). Only changed fields are sent; a blank
     * schedule/name is treated as "unchanged" by the repository layer. The list
     * refreshes on success so the row reflects the server's normalized record.
     */
    fun update(id: String, name: String, schedule: String, prompt: String) {
        if (!config.isComplete || name.isBlank() || schedule.isBlank()) return
        viewModelScope.launch {
            when (val res = repository.updateJob(config, id, name = name, schedule = schedule, prompt = prompt)) {
                is ApiResult.Success -> refresh()
                is ApiResult.Failure -> _state.update { it.copy(errorKind = res.kind) }
            }
        }
    }

    fun pause(id: String) = act(id, JobAction.PAUSE)
    fun resume(id: String) = act(id, JobAction.RESUME)

    fun runNow(id: String) {
        if (!config.isComplete) return
        viewModelScope.launch {
            when (repository.jobAction(config, id, JobAction.RUN)) {
                is ApiResult.Success -> _state.update { it.copy(notice = JobsUiState.Notice.RUN_TRIGGERED) }
                is ApiResult.Failure -> _state.update { it.copy(notice = JobsUiState.Notice.ACTION_FAILED) }
            }
        }
    }

    fun delete(id: String) {
        if (!config.isComplete) return
        viewModelScope.launch {
            when (repository.deleteJob(config, id)) {
                is ApiResult.Success -> refresh()
                is ApiResult.Failure -> _state.update { it.copy(notice = JobsUiState.Notice.ACTION_FAILED) }
            }
        }
    }

    fun consumeNotice() = _state.update { it.copy(notice = null) }

    private fun act(id: String, action: JobAction) {
        if (!config.isComplete) return
        viewModelScope.launch {
            when (repository.jobAction(config, id, action)) {
                is ApiResult.Success -> refresh()
                is ApiResult.Failure -> _state.update { it.copy(notice = JobsUiState.Notice.ACTION_FAILED) }
            }
        }
    }

    class Factory(private val repository: HermesRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            JobsViewModel(repository) as T
    }
}
