package com.a9ito.hermesagent.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.a9ito.hermesagent.core.CertPin
import com.a9ito.hermesagent.core.ConnectionConfig
import com.a9ito.hermesagent.core.ProfileRoute
import com.a9ito.hermesagent.data.crypto.TokenCrypto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// One process-wide DataStore instance, file lives under the app's datastore/ dir
// (excluded from cloud/device backup — see backup_rules.xml).
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hermes_connection")

/**
 * Persists the connection config. The base URL is stored in plaintext (not a
 * secret); the token is stored only as Keystore-encrypted ciphertext via
 * [TokenCrypto]. The plaintext token is never persisted or logged.
 */
class SettingsRepository(
    private val context: Context,
    private val crypto: TokenCrypto = TokenCrypto(),
) {

    /** Reactive connection config. Emits [ConnectionConfig.EMPTY] until set. */
    val connectionFlow: Flow<ConnectionConfig> = context.dataStore.data.map { prefs ->
        val baseUrl = prefs[KEY_BASE_URL].orEmpty()
        val token = prefs[KEY_TOKEN_ENC]?.let { crypto.decrypt(it) }.orEmpty()
        val profile = ProfileRoute.normalize(prefs[KEY_PROFILE]) ?: ProfileRoute.DEFAULT
        val certPins = CertPin.pinsOrEmpty(prefs[KEY_CERT_PINS])
        ConnectionConfig(baseUrl = baseUrl, token = token, profile = profile, certPins = certPins)
    }

    /**
     * Persist a normalized [baseUrl] + raw [token] + multiplex [profile] + TLS
     * [certPinsRaw]. The token is encrypted before it touches disk; the profile is
     * normalized (blank/invalid -> default); the pins are stored only when they
     * parse (blank/invalid -> no pinning) so a bad value never reaches the client.
     */
    suspend fun save(
        baseUrl: String,
        token: String,
        profile: String = ProfileRoute.DEFAULT,
        certPinsRaw: String = "",
    ) {
        val encryptedToken = crypto.encrypt(token)
        val normalizedProfile = ProfileRoute.normalize(profile) ?: ProfileRoute.DEFAULT
        val normalizedPins = CertPin.pinsOrEmpty(certPinsRaw).joinToString("\n")
        context.dataStore.edit { prefs ->
            prefs[KEY_BASE_URL] = baseUrl
            prefs[KEY_TOKEN_ENC] = encryptedToken
            prefs[KEY_PROFILE] = normalizedProfile
            if (normalizedPins.isEmpty()) prefs.remove(KEY_CERT_PINS) else prefs[KEY_CERT_PINS] = normalizedPins
        }
    }

    /** Wipe the stored connection (used by "clear saved connection"). */
    suspend fun clear() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_BASE_URL)
            prefs.remove(KEY_TOKEN_ENC)
            prefs.remove(KEY_PROFILE)
            prefs.remove(KEY_CERT_PINS)
        }
    }

    private companion object {
        val KEY_BASE_URL = stringPreferencesKey("base_url")
        val KEY_TOKEN_ENC = stringPreferencesKey("token_enc")
        val KEY_PROFILE = stringPreferencesKey("profile")
        val KEY_CERT_PINS = stringPreferencesKey("cert_pins")
    }
}
