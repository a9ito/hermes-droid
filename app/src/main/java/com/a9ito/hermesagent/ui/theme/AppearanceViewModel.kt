package com.a9ito.hermesagent.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.a9ito.hermesagent.core.AccentPreset
import com.a9ito.hermesagent.core.AppearancePrefs
import com.a9ito.hermesagent.core.CornerStyle
import com.a9ito.hermesagent.core.FontChoice
import com.a9ito.hermesagent.core.ThemeMode
import com.a9ito.hermesagent.core.UiScale
import com.a9ito.hermesagent.data.AppearanceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Exposes the persisted [AppearancePrefs] as state for the theme + the settings
 * UI, and writes changes back through [AppearanceRepository]. Scoped at the app
 * root so the whole UI recomposes against one source of truth.
 */
class AppearanceViewModel(
    private val repository: AppearanceRepository,
) : ViewModel() {

    val prefs: StateFlow<AppearancePrefs> = repository.appearanceFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppearancePrefs.DEFAULT,
    )

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { repository.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { repository.setDynamicColor(enabled) }
    fun setPureBlack(enabled: Boolean) = viewModelScope.launch { repository.setPureBlack(enabled) }
    fun setAccent(accent: AccentPreset) = viewModelScope.launch { repository.setAccent(accent) }
    fun setFont(font: FontChoice) = viewModelScope.launch { repository.setFont(font) }
    fun setUiScale(scale: UiScale) = viewModelScope.launch { repository.setUiScale(scale) }
    fun setCornerStyle(style: CornerStyle) = viewModelScope.launch { repository.setCornerStyle(style) }

    class Factory(private val repository: AppearanceRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AppearanceViewModel(repository) as T
    }
}
