package com.a9ito.hermesagent.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.a9ito.hermesagent.core.ConnectionConfig
import com.a9ito.hermesagent.core.ErrorKind
import com.a9ito.hermesagent.core.ModelOptions
import com.a9ito.hermesagent.core.SessionSummary
import com.a9ito.hermesagent.core.filteredBy
import com.a9ito.hermesagent.core.sortedForDisplay
import com.a9ito.hermesagent.data.ApiResult
import com.a9ito.hermesagent.data.HermesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionsUiState(
    val configured: Boolean = false,
    val configLoaded: Boolean = false,
    val loading: Boolean = false,
    val sessions: List<SessionSummary> = emptyList(),
    val errorKind: ErrorKind? = null,
    /** Session id whose chat should be opened; consumed by the screen. */
    val openSessionId: String? = null,
    /** Live search query for filtering the list (title/model/preview). */
    val query: String = "",
    /** Flat /v1/models id list, fallback when model_options is unavailable. */
    val availableModels: List<String> = emptyList(),
    /** Rich provider catalog when the instance advertises model_options; null otherwise. */
    val modelOptions: ModelOptions? = null,
    /** True while a picker-triggered fresh /api/model/options fetch is in flight. */
    val refreshingModels: Boolean = false,
) {
    /** Sessions actually shown: server list filtered by [query] (already sorted). */
    val visibleSessions: List<SessionSummary> get() = sessions.filteredBy(query)
}

/**
 * Owns the persisted-session list: load, create, fork, rename, delete. Selecting
 * a session sets [SessionsUiState.openSessionId], which the screen turns into a
 * navigation to the session-scoped chat.
 */
class SessionsViewModel(
    private val repository: HermesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SessionsUiState())
    val state: StateFlow<SessionsUiState> = _state.asStateFlow()

    private var config: ConnectionConfig = ConnectionConfig.EMPTY

    init {
        viewModelScope.launch {
            repository.connectionFlow.collect { c ->
                val firstConfigured = c.isComplete && !config.isComplete
                config = c
                _state.update { it.copy(configured = c.isComplete, configLoaded = true) }
                if (firstConfigured) {
                    refresh()
                    loadModelCatalog()
                }
            }
        }
    }

    /**
     * Load the model catalog for the new-session picker. Prefer the rich
     * /api/model/options catalog; fall back to the flat /v1/models id list.
     * Failures are non-fatal — the picker just stays hidden — so they never
     * touch errorKind.
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
     * pricing/entitlement synchronously. Called when the new-session picker
     * opens, so the ~15s round-trip only happens on demand. Mirrors the
     * chat/runs treatment.
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

    fun refresh() {
        if (!config.isComplete) {
            _state.update { it.copy(errorKind = ErrorKind.NO_CONNECTION, sessions = emptyList()) }
            return
        }
        _state.update { it.copy(loading = true, errorKind = null) }
        viewModelScope.launch {
            when (val res = repository.listSessions(config)) {
                is ApiResult.Success ->
                    _state.update { it.copy(loading = false, sessions = res.data.sortedForDisplay(), errorKind = null) }
                is ApiResult.Failure ->
                    _state.update { it.copy(loading = false, errorKind = res.kind) }
            }
        }
    }

    /**
     * Create a new session and open it immediately. [model] optionally pins the
     * session to a model, [systemPrompt] optionally seeds a custom system prompt;
     * both default to null for a plain new session (the FAB path).
     */
    fun createAndOpen(title: String? = null, model: String? = null, systemPrompt: String? = null) {
        if (!config.isComplete) return
        viewModelScope.launch {
            when (val res = repository.createSession(config, title, model, systemPrompt)) {
                is ApiResult.Success -> {
                    refresh()
                    _state.update { it.copy(openSessionId = res.data.id) }
                }
                is ApiResult.Failure -> _state.update { it.copy(errorKind = res.kind) }
            }
        }
    }

    fun open(id: String) = _state.update { it.copy(openSessionId = id) }
    fun consumeOpen() = _state.update { it.copy(openSessionId = null) }

    /** Update the live search query; filtering is derived, so no network call. */
    fun onQueryChange(value: String) = _state.update { it.copy(query = value) }

    fun delete(id: String) {
        if (!config.isComplete) return
        viewModelScope.launch {
            when (val res = repository.deleteSession(config, id)) {
                is ApiResult.Success -> refresh()
                is ApiResult.Failure -> _state.update { it.copy(errorKind = res.kind) }
            }
        }
    }

    fun fork(id: String) {
        if (!config.isComplete) return
        viewModelScope.launch {
            when (val res = repository.forkSession(config, id)) {
                is ApiResult.Success -> refresh()
                is ApiResult.Failure -> _state.update { it.copy(errorKind = res.kind) }
            }
        }
    }

    fun rename(id: String, title: String) {
        if (!config.isComplete || title.isBlank()) return
        viewModelScope.launch {
            when (val res = repository.renameSession(config, id, title.trim())) {
                is ApiResult.Success -> refresh()
                is ApiResult.Failure -> _state.update { it.copy(errorKind = res.kind) }
            }
        }
    }

    /** Toggle pin: pass the session's CURRENT pinned state; we flip it. */
    fun togglePin(id: String, currentlyPinned: Boolean) {
        if (!config.isComplete) return
        viewModelScope.launch {
            when (val res = repository.setSessionPinned(config, id, !currentlyPinned)) {
                is ApiResult.Success -> refresh()
                is ApiResult.Failure -> _state.update { it.copy(errorKind = res.kind) }
            }
        }
    }

    /**
     * Archive a session. Archived rows drop out of the default list (verified
     * against the server: no include_archived surface exists on this endpoint),
     * so this is effectively a soft-hide that keeps the transcript on the
     * instance and reachable from desktop.
     */
    fun archive(id: String) {
        if (!config.isComplete) return
        viewModelScope.launch {
            when (val res = repository.setSessionArchived(config, id, true)) {
                is ApiResult.Success -> refresh()
                is ApiResult.Failure -> _state.update { it.copy(errorKind = res.kind) }
            }
        }
    }

    class Factory(private val repository: HermesRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SessionsViewModel(repository) as T
    }
}
