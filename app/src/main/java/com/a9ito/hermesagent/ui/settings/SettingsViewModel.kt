package com.a9ito.hermesagent.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.a9ito.hermesagent.core.CertPin
import com.a9ito.hermesagent.core.CleartextPolicy
import com.a9ito.hermesagent.core.ConnectionConfig
import com.a9ito.hermesagent.core.ProfileRoute
import com.a9ito.hermesagent.core.UrlNormalizer
import com.a9ito.hermesagent.data.HermesRepository
import com.a9ito.hermesagent.data.SettingsRepository
import com.a9ito.hermesagent.data.remote.CertificateProbe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which field, if any, failed validation — screen maps this to a string res. */
enum class SettingsField { HOST, PORT, TOKEN, PROFILE, CERT_PINS }

/** Outcome of a trust-on-first-use pin capture, mapped to a message by the screen. */
enum class CaptureOutcome { CAPTURED, ALREADY_PRESENT, NOT_HTTPS, BAD_URL, FAILED }

data class SettingsUiState(
    val host: String = "",
    val port: String = "",
    val token: String = "",
    val profile: String = "",
    val certPins: String = "",
    val tokenVisible: Boolean = false,
    val resolvedEndpoint: String? = null,
    val invalidField: SettingsField? = null,
    val saved: Boolean = false,
    val cleared: Boolean = false,
    val loaded: Boolean = false,
    /** True while a TOFU certificate capture is in flight. */
    val capturing: Boolean = false,
    /** One-shot result of the last capture; consumed by the screen after it shows a message. */
    val captureOutcome: CaptureOutcome? = null,
    /**
     * Set to the resolved base URL when a save was held back because it targets
     * cleartext HTTP on a non-local host; the UI shows a confirmation dialog and
     * calls [confirmSaveCleartext] to proceed or [dismissCleartextWarning] to cancel.
     */
    val pendingCleartextUrl: String? = null,
) {
    /** True when the typed host resolves to an https URL a pin can be captured from. */
    val canCapturePin: Boolean
        get() = (UrlNormalizer.normalize(host, port) as? UrlNormalizer.Result.Ok)
            ?.let { CertificateProbe.isPinnable(it.baseUrl) } == true
}

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val hermes: HermesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        // Prefill the form ONCE from any saved connection, then stop: a perpetual
        // collect would re-decrypt the token on every later settings change only to
        // discard it (the `loaded` guard). first() captures the current value and
        // completes. The token is decrypted only into the in-memory form field; it
        // is never logged.
        viewModelScope.launch {
            val config = settings.connectionFlow.first()
            val (host, port) = splitBaseUrl(config.baseUrl)
            _state.update {
                it.copy(
                    host = host,
                    port = port,
                    token = config.token,
                    profile = if (config.profile == ProfileRoute.DEFAULT) "" else config.profile,
                    certPins = config.certPins.joinToString("\n"),
                    resolvedEndpoint = config.baseUrl.ifBlank { null },
                    loaded = true,
                )
            }
        }
    }

    fun onHostChange(value: String) = _state.update { it.copy(host = value, invalidField = null, saved = false) }
    fun onPortChange(value: String) = _state.update { it.copy(port = value.filter(Char::isDigit), invalidField = null, saved = false) }
    fun onTokenChange(value: String) = _state.update { it.copy(token = value, invalidField = null, saved = false) }
    fun onProfileChange(value: String) = _state.update { it.copy(profile = value, invalidField = null, saved = false) }
    fun onCertPinsChange(value: String) = _state.update { it.copy(certPins = value, invalidField = null, saved = false) }
    fun toggleTokenVisibility() = _state.update { it.copy(tokenVisible = !it.tokenVisible) }
    fun consumeEvents() = _state.update { it.copy(saved = false, cleared = false) }
    fun consumeCaptureOutcome() = _state.update { it.copy(captureOutcome = null) }

    /**
     * Trust-on-first-use: open a validated HTTPS connection to the typed host and
     * append its leaf certificate pin to the field (de-duplicated). No token is
     * sent by the probe. Requires https; a cleartext or unparseable URL yields a
     * [CaptureOutcome] the screen turns into a message instead of a pin. The pin
     * is only placed into the editable field — the user still has to Save to
     * persist it, so a mistaken capture is trivially discarded.
     */
    fun captureCertPin() {
        val s = _state.value
        if (s.capturing) return
        val normalized = UrlNormalizer.normalize(s.host, s.port)
        if (normalized !is UrlNormalizer.Result.Ok) {
            _state.update { it.copy(captureOutcome = CaptureOutcome.BAD_URL) }
            return
        }
        // Build a config that targets the typed endpoint (+ any profile prefix) so
        // the probe hits exactly what a save would connect to.
        val profile = ProfileRoute.normalize(s.profile) ?: ProfileRoute.DEFAULT
        val config = ConnectionConfig(baseUrl = normalized.baseUrl, token = s.token, profile = profile)
        _state.update { it.copy(capturing = true, captureOutcome = null) }
        viewModelScope.launch {
            val outcome = when (val r = hermes.captureCertPin(config)) {
                is CertificateProbe.Result.Ok -> {
                    val existing = CertPin.pinsOrEmpty(s.certPins)
                    if (r.pin in existing) {
                        CaptureOutcome.ALREADY_PRESENT
                    } else {
                        val merged = (existing + r.pin).joinToString("\n")
                        _state.update { it.copy(certPins = merged, invalidField = null, saved = false) }
                        CaptureOutcome.CAPTURED
                    }
                }
                CertificateProbe.Result.NotHttps -> CaptureOutcome.NOT_HTTPS
                is CertificateProbe.Result.Failed -> CaptureOutcome.FAILED
            }
            _state.update { it.copy(capturing = false, captureOutcome = outcome) }
        }
    }

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
        // Cert pins are optional, but if present every token must parse — a typo'd
        // pin must not be silently dropped (that would leave the user thinking they
        // are pinned when they are not).
        if (CertPin.parse(s.certPins) is CertPin.Result.Invalid) {
            _state.update { it.copy(invalidField = SettingsField.CERT_PINS) }
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
                persist(result.baseUrl, s.token, normalizedProfile, s.certPins)
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
        persist(url, s.token, normalizedProfile, s.certPins)
    }

    /** Dismiss the cleartext confirmation without saving. */
    fun dismissCleartextWarning() = _state.update { it.copy(pendingCleartextUrl = null) }

    private fun persist(baseUrl: String, token: String, profile: String, certPinsRaw: String) {
        viewModelScope.launch {
            settings.save(baseUrl = baseUrl, token = token, profile = profile, certPinsRaw = certPinsRaw)
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

    class Factory(
        private val settings: SettingsRepository,
        private val hermes: HermesRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(settings, hermes) as T
    }
}
