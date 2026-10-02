package com.a9ito.hermesagent.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.a9ito.hermesagent.core.AccentPreset
import com.a9ito.hermesagent.core.AppearancePrefs

/**
 * Hand-picked static fallback schemes, used on devices below Android 12 (where
 * dynamic color is unavailable) — a real branded plum palette, never the
 * placeholder baseline grey/purple.
 */
internal val LightColors: ColorScheme = lightColorScheme(
    primary = md_primary_light,
    onPrimary = md_onPrimary_light,
    primaryContainer = md_primaryContainer_light,
    onPrimaryContainer = md_onPrimaryContainer_light,
    secondary = md_secondary_light,
    onSecondary = md_onSecondary_light,
    secondaryContainer = md_secondaryContainer_light,
    onSecondaryContainer = md_onSecondaryContainer_light,
    tertiary = md_tertiary_light,
    onTertiary = md_onTertiary_light,
    tertiaryContainer = md_tertiaryContainer_light,
    onTertiaryContainer = md_onTertiaryContainer_light,
    error = md_error_light,
    onError = md_onError_light,
    errorContainer = md_errorContainer_light,
    onErrorContainer = md_onErrorContainer_light,
    background = md_background_light,
    onBackground = md_onBackground_light,
    surface = md_surface_light,
    onSurface = md_onSurface_light,
    surfaceVariant = md_surfaceVariant_light,
    onSurfaceVariant = md_onSurfaceVariant_light,
    outline = md_outline_light,
    outlineVariant = md_outlineVariant_light,
    inverseSurface = md_inverseSurface_light,
    inverseOnSurface = md_inverseOnSurface_light,
    inversePrimary = md_inversePrimary_light,
    surfaceTint = md_surfaceTint_light,
)

internal val DarkColors: ColorScheme = darkColorScheme(
    primary = md_primary_dark,
    onPrimary = md_onPrimary_dark,
    primaryContainer = md_primaryContainer_dark,
    onPrimaryContainer = md_onPrimaryContainer_dark,
    secondary = md_secondary_dark,
    onSecondary = md_onSecondary_dark,
    secondaryContainer = md_secondaryContainer_dark,
    onSecondaryContainer = md_onSecondaryContainer_dark,
    tertiary = md_tertiary_dark,
    onTertiary = md_onTertiary_dark,
    tertiaryContainer = md_tertiaryContainer_dark,
    onTertiaryContainer = md_onTertiaryContainer_dark,
    error = md_error_dark,
    onError = md_onError_dark,
    errorContainer = md_errorContainer_dark,
    onErrorContainer = md_onErrorContainer_dark,
    background = md_background_dark,
    onBackground = md_onBackground_dark,
    surface = md_surface_dark,
    onSurface = md_onSurface_dark,
    surfaceVariant = md_surfaceVariant_dark,
    onSurfaceVariant = md_onSurfaceVariant_dark,
    outline = md_outline_dark,
    outlineVariant = md_outlineVariant_dark,
    inverseSurface = md_inverseSurface_dark,
    inverseOnSurface = md_inverseOnSurface_dark,
    inversePrimary = md_inversePrimary_dark,
    surfaceTint = md_surfaceTint_dark,
)

/**
 * Blacken the dark background + surface roles for OLED ("pure black" toggle).
 * Only the dark scheme should be passed here; applies true black to the base
 * surfaces while leaving container tones intact so elevation is still readable.
 */
private fun ColorScheme.toPureBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
)

/**
 * App theme built on Material 3, styled for the Expressive look, driven by the
 * user's [AppearancePrefs].
 *
 * - [AppearancePrefs.themeMode] forces light/dark or follows the system.
 * - [AppearancePrefs.dynamicColor] uses Android 12+ wallpaper-based dynamic
 *   color when on and available; otherwise the chosen [AppearancePrefs.accent]
 *   curated palette (dependency-free, hand-picked — not a generated seed).
 * - [AppearancePrefs.pureBlack] blacks out dark surfaces for OLED (dark only).
 * - Motion is spring-based: the public [MaterialTheme] installs material3's
 *   standard [androidx.compose.material3.MotionScheme], whose specs are all
 *   `spring(...)`, so components animate with springs out of the box.
 * - Expressive shape and type tokens come from [AppShapes] and [AppTypography].
 *
 * Note on the fully-public Expressive API: `MaterialExpressiveTheme` and
 * `MotionScheme.expressive()` are still library-`internal` in material3 1.4.0
 * (the current stable); their public form ships in material3 1.5.0+ (compileSdk
 * 37, not yet stable). We drive Expressive through the public [MaterialTheme]
 * surface; swap in `MaterialExpressiveTheme` once 1.5.0 is stable.
 */
@Composable
fun HermesAgentTheme(
    prefs: AppearancePrefs = AppearancePrefs.DEFAULT,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = prefs.resolveDark(systemDark)
    val useDynamic = prefs.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    var colorScheme: ColorScheme = when {
        useDynamic -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> accentDarkScheme(prefs.accent)
        else -> accentLightScheme(prefs.accent)
    }
    if (darkTheme && prefs.pureBlack) colorScheme = colorScheme.toPureBlack()

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
