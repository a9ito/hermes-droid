package com.a9ito.hermesagent.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.a9ito.hermesagent.core.AccentPreset
import com.a9ito.hermesagent.core.AppearancePrefs
import com.a9ito.hermesagent.core.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Appearance settings live in their own DataStore file, separate from the
// connection store, so clearing the saved connection never touches the user's
// look-and-feel choices.
private val Context.appearanceDataStore: DataStore<Preferences> by preferencesDataStore(name = "hermes_appearance")

/**
 * Persists the user's [AppearancePrefs]. All values are plaintext UI preferences
 * (no secrets). Enums are stored by their stable string [key] so a reordering of
 * the enum constants never corrupts a saved choice.
 */
class AppearanceRepository(private val context: Context) {

    /** Reactive appearance prefs. Emits [AppearancePrefs.DEFAULT] until set. */
    val appearanceFlow: Flow<AppearancePrefs> = context.appearanceDataStore.data.map { prefs ->
        AppearancePrefs(
            themeMode = ThemeMode.fromKey(prefs[KEY_THEME_MODE]),
            dynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: true,
            pureBlack = prefs[KEY_PURE_BLACK] ?: false,
            accent = AccentPreset.fromKey(prefs[KEY_ACCENT]),
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) =
        context.appearanceDataStore.edit { it[KEY_THEME_MODE] = mode.key }.let {}

    suspend fun setDynamicColor(enabled: Boolean) =
        context.appearanceDataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }.let {}

    suspend fun setPureBlack(enabled: Boolean) =
        context.appearanceDataStore.edit { it[KEY_PURE_BLACK] = enabled }.let {}

    suspend fun setAccent(accent: AccentPreset) =
        context.appearanceDataStore.edit { it[KEY_ACCENT] = accent.key }.let {}

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_PURE_BLACK = booleanPreferencesKey("pure_black")
        val KEY_ACCENT = stringPreferencesKey("accent")
    }
}
