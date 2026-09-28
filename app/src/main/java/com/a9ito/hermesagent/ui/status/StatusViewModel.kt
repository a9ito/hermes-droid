package com.a9ito.hermesagent.ui.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.a9ito.hermesagent.core.Capabilities
import com.a9ito.hermesagent.core.ConnectionConfig
import com.a9ito.hermesagent.core.ErrorKind
import com.a9ito.hermesagent.core.InstanceStatus
import com.a9ito.hermesagent.data.ApiResult
import com.a9ito.hermesagent.data.HermesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StatusUiState(
    val configured: Boolean = false,
    val configLoaded: Boolean = false,
    val loading: Boolean = false,
    val status: InstanceStatus? = null,
    val capabilities: Capabilities? = null,
    val errorKind: ErrorKind? = null,
)

class StatusViewModel(
    private val repository: HermesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(StatusUiState())
    val state: StateFlow<StatusUiState> = _state.asStateFlow()

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
            _state.update { it.copy(errorKind = ErrorKind.NO_CONNECTION, status = null) }
            return
        }
        _state.update { it.copy(loading = true, errorKind = null) }
        viewModelScope.launch {
            when (val res = repository.fetchStatus(config)) {
                is ApiResult.Success ->
                    _state.update { it.copy(loading = false, status = res.data, errorKind = null) }
                is ApiResult.Failure ->
                    _state.update { it.copy(loading = false, status = null, errorKind = res.kind) }
            }
            // Capabilities are best-effort context, not gating for this screen:
            // a failure here shouldn't blank the status the user just fetched.
            when (val caps = repository.fetchCapabilities(config)) {
                is ApiResult.Success -> _state.update { it.copy(capabilities = caps.data) }
                is ApiResult.Failure -> Unit
            }
        }
    }

    class Factory(private val repository: HermesRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            StatusViewModel(repository) as T
    }
}
