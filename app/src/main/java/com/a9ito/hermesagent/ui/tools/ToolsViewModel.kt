package com.a9ito.hermesagent.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.a9ito.hermesagent.core.ConnectionConfig
import com.a9ito.hermesagent.core.ErrorKind
import com.a9ito.hermesagent.data.ApiResult
import com.a9ito.hermesagent.data.HermesRepository
import com.a9ito.hermesagent.data.remote.dto.SkillDto
import com.a9ito.hermesagent.data.remote.dto.ToolsetDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ToolsTab { SKILLS, TOOLSETS }

data class ToolsUiState(
    val configured: Boolean = false,
    val configLoaded: Boolean = false,
    val tab: ToolsTab = ToolsTab.SKILLS,
    val loading: Boolean = false,
    val skills: List<SkillDto> = emptyList(),
    val toolsets: List<ToolsetDto> = emptyList(),
    val errorKind: ErrorKind? = null,
)

/**
 * Read-only viewer for the instance's installed skills and configurable
 * toolsets (GET /v1/skills, GET /v1/toolsets). Nothing here mutates server
 * state — it surfaces what the connected Hermes exposes.
 */
class ToolsViewModel(
    private val repository: HermesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ToolsUiState())
    val state: StateFlow<ToolsUiState> = _state.asStateFlow()

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

    fun selectTab(tab: ToolsTab) {
        if (_state.value.tab == tab) return
        _state.update { it.copy(tab = tab) }
        refresh()
    }

    fun refresh() {
        if (!config.isComplete) {
            _state.update { it.copy(errorKind = ErrorKind.NO_CONNECTION) }
            return
        }
        _state.update { it.copy(loading = true, errorKind = null) }
        viewModelScope.launch {
            when (_state.value.tab) {
                ToolsTab.SKILLS -> when (val res = repository.fetchSkills(config)) {
                    is ApiResult.Success -> _state.update { it.copy(loading = false, skills = res.data, errorKind = null) }
                    is ApiResult.Failure -> _state.update { it.copy(loading = false, errorKind = res.kind) }
                }
                ToolsTab.TOOLSETS -> when (val res = repository.fetchToolsets(config)) {
                    is ApiResult.Success -> _state.update { it.copy(loading = false, toolsets = res.data, errorKind = null) }
                    is ApiResult.Failure -> _state.update { it.copy(loading = false, errorKind = res.kind) }
                }
            }
        }
    }

    class Factory(private val repository: HermesRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ToolsViewModel(repository) as T
    }
}
