package com.a9ito.hermesagent.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.a9ito.hermesagent.core.UrlNormalizer
import com.a9ito.hermesagent.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which field, if any, failed validation — screen maps this to a string res. */
enum class SettingsField { HOST, PORT, TOKEN }

data class SettingsUiState(
    val host: String = "",
    val port: String = "",
    val token: String = "",
    val tokenVisible: Boolean = false,
    val resolvedEndpoint: String? = null,
    val invalidField: SettingsField? = null,
    val saved: Boolean = false,
    val cleared: Boolean = false,
    val loaded: Boolean = false,
)

class SettingsViewModel(
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        // Prefill the form from any saved connection. The token is decrypted only
        // into the in-memory form field; it is never logged.
        viewModelScope.launch {
            settings.connectionFlow.collect { config ->
                if (!_state.value.loaded) {
                    val (host, port) = splitBaseUrl(config.baseUrl)
                    _state.update {
                        it.copy(
                            host = host,
                            port = port,
                            token = config.token,
                            resolvedEndpoint = config.baseUrl.ifBlank { null },
                            loaded = true,
                        )
                    }
                }
            }
        }
    }

    fun onHostChange(value: String) = _state.update { it.copy(host = value, invalidField = null, saved = false) }
    fun onPortChange(value: String) = _state.update { it.copy(port = value.filter(Char::isDigit), invalidField = null, saved = false) }
    fun onTokenChange(value: String) = _state.update { it.copy(token = value, invalidField = null, saved = false) }
    fun toggleTokenVisibility() = _state.update { it.copy(tokenVisible = !it.tokenVisible) }
    fun consumeEvents() = _state.update { it.copy(saved = false, cleared = false) }

    /** Validate + persist. Returns nothing; UI observes [state]. */
    fun save() {
        val s = _state.value
        if (s.token.isBlank()) {
            _state.update { it.copy(invalidField = SettingsField.TOKEN) }
            return
        }
        when (val result = UrlNormalizer.normalize(s.host, s.port)) {
            is UrlNormalizer.Result.Ok -> {
                viewModelScope.launch {
                    settings.save(baseUrl = result.baseUrl, token = s.token)
                    _state.update {
                        it.copy(resolvedEndpoint = result.baseUrl, saved = true, invalidField = null)
                    }
                }
            }
            UrlNormalizer.Result.EmptyHost ->
                _state.update { it.copy(invalidField = SettingsField.HOST) }
            is UrlNormalizer.Result.InvalidPort ->
                _state.update { it.copy(invalidField = SettingsField.PORT) }
            is UrlNormalizer.Result.InvalidHost ->
                _state.update { it.copy(invalidField = SettingsField.HOST) }
        }
    }

    fun clear() {
        viewModelScope.launch {
            settings.clear()
            _state.update {
                SettingsUiState(loaded = true, cleared = true)
            }
        }
    }

    /** Best-effort inverse of UrlNormalizer for prefilling the two fields. */
    private fun splitBaseUrl(baseUrl: String): Pair<String, String> {
        if (baseUrl.isBlank()) return "" to ""
        val schemeSep = baseUrl.indexOf("://")
        val afterScheme = if (schemeSep == -1) baseUrl else baseUrl.substring(schemeSep + 3)
        val authority = afterScheme.substringBefore('/')
        // Keep scheme+host together in the host field, port separate.
        val scheme = if (schemeSep == -1) "" else baseUrl.substring(0, schemeSep + 3)
        return if (!authority.startsWith("[") && authority.contains(':')) {
            val host = authority.substringBeforeLast(':')
            val port = authority.substringAfterLast(':')
            "$scheme$host" to port
        } else {
            "$scheme$authority" to ""
        }
    }

    class Factory(private val settings: SettingsRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(settings) as T
    }
}
