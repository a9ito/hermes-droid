package com.a9ito.hermesagent.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.a9ito.hermesagent.core.ConnectionConfig
import com.a9ito.hermesagent.core.ErrorKind
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
                if (firstConfigured) refresh()
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

    /** Create a new empty session and open it immediately. */
    fun createAndOpen(title: String? = null) {
        if (!config.isComplete) return
        viewModelScope.launch {
            when (val res = repository.createSession(config, title)) {
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
