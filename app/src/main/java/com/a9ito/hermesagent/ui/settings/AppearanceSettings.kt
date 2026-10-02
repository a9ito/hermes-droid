package com.a9ito.hermesagent.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.a9ito.hermesagent.R
import com.a9ito.hermesagent.core.AccentPreset
import com.a9ito.hermesagent.core.AppearancePrefs
import com.a9ito.hermesagent.core.CornerStyle
import com.a9ito.hermesagent.core.FontChoice
import com.a9ito.hermesagent.core.ThemeMode
import com.a9ito.hermesagent.core.UiScale
import com.a9ito.hermesagent.ui.theme.accentDarkScheme
import com.a9ito.hermesagent.ui.theme.accentLightScheme

/**
 * Stateless appearance controls, hoisted so the caller owns the [AppearancePrefs]
 * and the setters (the Settings screen wires them to AppearanceViewModel). Covers
 * theme mode, dynamic color, pure-black (OLED), and the curated accent preset.
 *
 * Dynamic color and pure-black have conditional relevance, surfaced in copy
 * rather than by hiding the control: dynamic color needs Android 12+, and the
 * accent swatches only drive the palette when dynamic color is off.
 */
@Composable
fun AppearanceSettings(
    prefs: AppearancePrefs,
    onThemeMode: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onPureBlack: (Boolean) -> Unit,
    onAccent: (AccentPreset) -> Unit,
    onFont: (FontChoice) -> Unit,
    onUiScale: (UiScale) -> Unit,
    onCornerStyle: (CornerStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.appearance_header),
                style = MaterialTheme.typography.titleMedium,
            )

            // --- Theme mode ---
            Text(stringResource(R.string.appearance_theme_mode))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeModeChip(ThemeMode.SYSTEM, prefs.themeMode, R.string.appearance_theme_system, onThemeMode)
                ThemeModeChip(ThemeMode.LIGHT, prefs.themeMode, R.string.appearance_theme_light, onThemeMode)
                ThemeModeChip(ThemeMode.DARK, prefs.themeMode, R.string.appearance_theme_dark, onThemeMode)
            }

            // --- Dynamic color ---
            ToggleRow(
                title = stringResource(R.string.appearance_dynamic_color),
                subtitle = stringResource(R.string.appearance_dynamic_color_sub),
                checked = prefs.dynamicColor,
                onCheckedChange = onDynamicColor,
            )

            // --- Pure black (OLED) ---
            ToggleRow(
                title = stringResource(R.string.appearance_pure_black),
                subtitle = stringResource(R.string.appearance_pure_black_sub),
                checked = prefs.pureBlack,
                onCheckedChange = onPureBlack,
            )

            // --- Accent preset ---
            Text(stringResource(R.string.appearance_accent))
            Text(
                text = stringResource(R.string.appearance_accent_sub),
                style = MaterialTheme.typography.bodySmall,
            )
            val dark = isSystemInDarkTheme().let { prefs.resolveDark(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccentPreset.entries.forEach { preset ->
                    val swatch = if (dark) accentDarkScheme(preset).primary else accentLightScheme(preset).primary
                    AccentSwatch(
                        color = swatch,
                        selected = prefs.accent == preset,
                        contentDescription = preset.key,
                        onClick = { onAccent(preset) },
                    )
                }
            }

            // --- Font ---
            Text(stringResource(R.string.appearance_font))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = prefs.font == FontChoice.SYSTEM,
                    onClick = { onFont(FontChoice.SYSTEM) },
                    label = { Text(stringResource(R.string.appearance_font_system)) },
                )
                FilterChip(
                    selected = prefs.font == FontChoice.SERIF,
                    onClick = { onFont(FontChoice.SERIF) },
                    label = { Text(stringResource(R.string.appearance_font_serif)) },
                )
                FilterChip(
                    selected = prefs.font == FontChoice.MONOSPACE,
                    onClick = { onFont(FontChoice.MONOSPACE) },
                    label = { Text(stringResource(R.string.appearance_font_mono)) },
                )
            }

            // --- UI scale ---
            Text(stringResource(R.string.appearance_ui_scale))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UiScale.entries.forEach { scale ->
                    FilterChip(
                        selected = prefs.uiScale == scale,
                        onClick = { onUiScale(scale) },
                        label = { Text(stringResource(uiScaleLabel(scale))) },
                    )
                }
            }

            // --- Corner style ---
            Text(stringResource(R.string.appearance_corners))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CornerStyle.entries.forEach { style ->
                    FilterChip(
                        selected = prefs.cornerStyle == style,
                        onClick = { onCornerStyle(style) },
                        label = { Text(stringResource(cornerLabel(style))) },
                    )
                }
            }
        }
    }
}

private fun uiScaleLabel(scale: UiScale): Int = when (scale) {
    UiScale.COMPACT -> R.string.appearance_scale_compact
    UiScale.DEFAULT -> R.string.appearance_scale_default
    UiScale.COMFORTABLE -> R.string.appearance_scale_comfortable
    UiScale.LARGE -> R.string.appearance_scale_large
}

private fun cornerLabel(style: CornerStyle): Int = when (style) {
    CornerStyle.SHARP -> R.string.appearance_corners_sharp
    CornerStyle.ROUNDED -> R.string.appearance_corners_rounded
    CornerStyle.EXTRA -> R.string.appearance_corners_extra
}

@Composable
private fun ThemeModeChip(
    mode: ThemeMode,
    current: ThemeMode,
    labelRes: Int,
    onSelect: (ThemeMode) -> Unit,
) {
    FilterChip(
        selected = current == mode,
        onClick = { onSelect(mode) },
        label = { Text(stringResource(labelRes)) },
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AccentSwatch(
    color: Color,
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val border = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
    Surface(
        color = color,
        shape = CircleShape,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .border(width = if (selected) 3.dp else 1.dp, color = border, shape = CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.surface,
                )
            }
        }
    }
}
