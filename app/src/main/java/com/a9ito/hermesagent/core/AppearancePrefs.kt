package com.a9ito.hermesagent.core

/**
 * Pure, Android-free appearance preferences model — enums + the resolver logic
 * that decides light-vs-dark, with NO Compose or Android types. The theme layer
 * (ui/theme) reads this to build the actual ColorScheme/Shapes/Typography, and
 * the DataStore-backed AppearanceRepository persists it. Keeping it pure lets
 * the resolve/parse rules be unit-tested on the plain JVM.
 *
 * Dependency-free by design: accent is a curated PRESET (a hand-picked palette),
 * not a free seed color run through a generative palette library.
 */

/** Which base theme to render. SYSTEM follows the OS dark-mode setting. */
enum class ThemeMode(val key: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        val DEFAULT = SYSTEM
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/**
 * Curated accent palettes used when dynamic color is OFF or unavailable
 * (below Android 12). [PLUM] is the app's branded default and matches the
 * historical static palette. Each maps to a hand-picked tonal family in
 * ui/theme/Color.kt.
 */
enum class AccentPreset(val key: String) {
    PLUM("plum"),
    INDIGO("indigo"),
    TEAL("teal"),
    FOREST("forest"),
    AMBER("amber"),
    ROSE("rose");

    companion object {
        val DEFAULT = PLUM
        fun fromKey(key: String?): AccentPreset = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/**
 * Typeface family for the whole type ramp. All three are built into the
 * platform, so none adds a font asset to the APK (dependency-free). SYSTEM is
 * the default sans-serif the app always used.
 */
enum class FontChoice(val key: String) {
    SYSTEM("system"),
    SERIF("serif"),
    MONOSPACE("monospace");

    companion object {
        val DEFAULT = SYSTEM
        fun fromKey(key: String?): FontChoice = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/**
 * Whole-UI zoom. [scale] multiplies the display density, so dp sizes AND sp text
 * rescale together (the user's OS accessibility font scale is preserved on top).
 * Range is kept gentle (0.9x..1.15x) so layouts don't break at the extremes.
 */
enum class UiScale(val key: String, val scale: Float) {
    COMPACT("compact", 0.9f),
    DEFAULT("default", 1.0f),
    COMFORTABLE("comfortable", 1.08f),
    LARGE("large", 1.15f);

    companion object {
        val DEFAULT_SCALE = DEFAULT
        fun fromKey(key: String?): UiScale = entries.firstOrNull { it.key == key } ?: DEFAULT_SCALE
    }
}

/**
 * Corner-radius personality for the M3 shape scale (cards, buttons, sheets,
 * dialogs). ROUNDED reproduces the app's original Expressive shape tokens.
 */
enum class CornerStyle(val key: String) {
    SHARP("sharp"),
    ROUNDED("rounded"),
    EXTRA("extra");

    companion object {
        val DEFAULT = ROUNDED
        fun fromKey(key: String?): CornerStyle = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/**
 * The full set of user appearance choices. Pure data; defaults reproduce the
 * app's original look (system theme mode, dynamic color on, branded plum accent
 * as the non-dynamic fallback, no pure-black, system font, default scale,
 * rounded corners).
 */
data class AppearancePrefs(
    val themeMode: ThemeMode = ThemeMode.DEFAULT,
    val dynamicColor: Boolean = true,
    val pureBlack: Boolean = false,
    val accent: AccentPreset = AccentPreset.DEFAULT,
    val font: FontChoice = FontChoice.DEFAULT,
    val uiScale: UiScale = UiScale.DEFAULT_SCALE,
    val cornerStyle: CornerStyle = CornerStyle.DEFAULT,
) {
    /**
     * Resolve whether the UI should render dark, given the OS dark-mode state.
     * SYSTEM defers to [systemInDark]; LIGHT/DARK force their respective mode.
     */
    fun resolveDark(systemInDark: Boolean): Boolean = when (themeMode) {
        ThemeMode.SYSTEM -> systemInDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    /**
     * Pure-black only has a visible effect in dark mode (it blacks out dark
     * surfaces for OLED). True when the resolved theme is dark AND the toggle is
     * on, so the theme layer need not re-derive the condition.
     */
    fun usePureBlack(systemInDark: Boolean): Boolean = pureBlack && resolveDark(systemInDark)

    companion object {
        val DEFAULT = AppearancePrefs()
    }
}
