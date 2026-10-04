package com.a9ito.hermesagent.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.a9ito.hermesagent.core.CleartextPolicy
import com.a9ito.hermesagent.core.ProfileRoute
import com.a9ito.hermesagent.core.UrlNormalizer
import com.a9ito.hermesagent.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which field, if any, failed validation — screen maps this to a string res. */
enum class SettingsField { HOST, PORT, TOKEN, PROFILE }

data class SettingsUiState(
    val host: String = "",
    val port: String = "",
    val token: String = "",
    val profile: String = "",
    val tokenVisible: Boolean = false,
    val resolvedEndpoint: String? = null,
    val invalidField: SettingsField? = null,
    val saved: Boolean = false,
    val cleared: Boolean = false,
    val loaded: Boolean = false,
    /**
     * Set to the resolved base URL when a save was held back because it targets
     * cleartext HTTP on a non-local host; the UI shows a confirmation dialog and
     * calls [confirmSaveCleartext] to proceed or [dismissCleartextWarning] to cancel.
     */
    val pendingCleartextUrl: String? = null,
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
                            profile = if (config.profile == ProfileRoute.DEFAULT) "" else config.profile,
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
    fun onProfileChange(value: String) = _state.update { it.copy(profile = value, invalidField = null, saved = false) }
    fun toggleTokenVisibility() = _state.update { it.copy(tokenVisible = !it.tokenVisible) }
    fun consumeEvents() = _state.update { it.copy(saved = false, cleared = false) }

    /** Validate + persist. Returns nothing; UI observes [state]. */
    fun save() {
        val s = _state.value
        if (s.token.isBlank()) {
            _state.update { it.copy(invalidField = SettingsField.TOKEN) }
            return
        }
        // A non-blank profile must be a valid profile-dir name; blank is fine (= default).
        val normalizedProfile = ProfileRoute.normalize(s.profile)
        if (normalizedProfile == null) {
            _state.update { it.copy(invalidField = SettingsField.PROFILE) }
            return
        }
        when (val result = UrlNormalizer.normalize(s.host, s.port)) {
            is UrlNormalizer.Result.Ok -> {
                // Sending a terminal-exec bearer token over plain HTTP to a
                // non-local host is a MITM risk; hold the save and let the UI
                // confirm. Loopback/LAN cleartext and all HTTPS save straight away.
                if (CleartextPolicy.requiresCleartextConfirmation(result.baseUrl)) {
                    _state.update { it.copy(pendingCleartextUrl = result.baseUrl, invalidField = null) }
                    return
                }
                persist(result.baseUrl, s.token, normalizedProfile)
            }
            UrlNormalizer.Result.EmptyHost ->
                _state.update { it.copy(invalidField = SettingsField.HOST) }
            is UrlNormalizer.Result.InvalidPort ->
                _state.update { it.copy(invalidField = SettingsField.PORT) }
            is UrlNormalizer.Result.InvalidHost ->
                _state.update { it.copy(invalidField = SettingsField.HOST) }
        }
    }

    /** Proceed with a save the user confirmed despite the cleartext warning. */
    fun confirmSaveCleartext() {
        val s = _state.value
        val url = s.pendingCleartextUrl ?: return
        val normalizedProfile = ProfileRoute.normalize(s.profile) ?: ProfileRoute.DEFAULT
        _state.update { it.copy(pendingCleartextUrl = null) }
        persist(url, s.token, normalizedProfile)
    }

    /** Dismiss the cleartext confirmation without saving. */
    fun dismissCleartextWarning() = _state.update { it.copy(pendingCleartextUrl = null) }

    private fun persist(baseUrl: String, token: String, profile: String) {
        viewModelScope.launch {
            settings.save(baseUrl = baseUrl, token = token, profile = profile)
            _state.update {
                it.copy(resolvedEndpoint = baseUrl, saved = true, invalidField = null)
            }
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
