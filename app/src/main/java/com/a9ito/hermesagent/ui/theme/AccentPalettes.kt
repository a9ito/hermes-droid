package com.a9ito.hermesagent.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.a9ito.hermesagent.core.AccentPreset

/**
 * Curated accent palettes for the non-dynamic color path (dynamic color off, or
 * Android below 12). Each [AccentPreset] supplies a hand-picked tonal family for
 * the primary/secondary/tertiary roles in both light and dark; the neutral
 * surface/background/error roles come from the base [LightColors] / [DarkColors]
 * in Theme.kt, so only the branded accent slots change between presets.
 *
 * Dependency-free: these are fixed, designer-chosen values, NOT generated from a
 * seed by a palette library. PLUM reproduces the app's original branded scheme.
 */

/** The accent-role tone values for one preset in one brightness. */
private data class AccentRoles(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val inversePrimary: Color,
)

/** Overlay this preset's accent roles onto a base neutral [ColorScheme]. */
private fun ColorScheme.withAccent(a: AccentRoles): ColorScheme = copy(
    primary = a.primary,
    onPrimary = a.onPrimary,
    primaryContainer = a.primaryContainer,
    onPrimaryContainer = a.onPrimaryContainer,
    secondary = a.secondary,
    onSecondary = a.onSecondary,
    secondaryContainer = a.secondaryContainer,
    onSecondaryContainer = a.onSecondaryContainer,
    tertiary = a.tertiary,
    onTertiary = a.onTertiary,
    tertiaryContainer = a.tertiaryContainer,
    onTertiaryContainer = a.onTertiaryContainer,
    inversePrimary = a.inversePrimary,
    surfaceTint = a.primary,
)

// --- Plum (branded default; matches the original static palette) ---
private val PlumLight = AccentRoles(
    primary = Color(0xFF5B4B8A), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6DEFF), onPrimaryContainer = Color(0xFF170F42),
    secondary = Color(0xFF605B71), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6DFF9), onSecondaryContainer = Color(0xFF1C182B),
    tertiary = Color(0xFF7D5260), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8E4), onTertiaryContainer = Color(0xFF31111D),
    inversePrimary = Color(0xFFCBBEFF),
)
private val PlumDark = AccentRoles(
    primary = Color(0xFFCBBEFF), onPrimary = Color(0xFF2C2059),
    primaryContainer = Color(0xFF433671), onPrimaryContainer = Color(0xFFE6DEFF),
    secondary = Color(0xFFCAC3DC), onSecondary = Color(0xFF322D41),
    secondaryContainer = Color(0xFF484458), onSecondaryContainer = Color(0xFFE6DFF9),
    tertiary = Color(0xFFEFB8C8), onTertiary = Color(0xFF4A2532),
    tertiaryContainer = Color(0xFF633B48), onTertiaryContainer = Color(0xFFFFD8E4),
    inversePrimary = Color(0xFF5B4B8A),
)

// --- Indigo ---
private val IndigoLight = AccentRoles(
    primary = Color(0xFF3F51B5), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDE1FF), onPrimaryContainer = Color(0xFF00105C),
    secondary = Color(0xFF5A5D72), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDFE1F9), onSecondaryContainer = Color(0xFF171B2C),
    tertiary = Color(0xFF76546E), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD7F2), onTertiaryContainer = Color(0xFF2C1228),
    inversePrimary = Color(0xFFBAC3FF),
)
private val IndigoDark = AccentRoles(
    primary = Color(0xFFBAC3FF), onPrimary = Color(0xFF07218A),
    primaryContainer = Color(0xFF263AA0), onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFFC3C5DD), onSecondary = Color(0xFF2C2F42),
    secondaryContainer = Color(0xFF424659), onSecondaryContainer = Color(0xFFDFE1F9),
    tertiary = Color(0xFFE5BAD8), onTertiary = Color(0xFF43273E),
    tertiaryContainer = Color(0xFF5C3D55), onTertiaryContainer = Color(0xFFFFD7F2),
    inversePrimary = Color(0xFF3F51B5),
)

// --- Teal ---
private val TealLight = AccentRoles(
    primary = Color(0xFF006A68), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF6FF7F3), onPrimaryContainer = Color(0xFF00201F),
    secondary = Color(0xFF4A6362), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E6), onSecondaryContainer = Color(0xFF051F1E),
    tertiary = Color(0xFF4B607C), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD3E4FF), onTertiaryContainer = Color(0xFF041C35),
    inversePrimary = Color(0xFF4CDAD7),
)
private val TealDark = AccentRoles(
    primary = Color(0xFF4CDAD7), onPrimary = Color(0xFF003736),
    primaryContainer = Color(0xFF00504E), onPrimaryContainer = Color(0xFF6FF7F3),
    secondary = Color(0xFFB0CCCA), onSecondary = Color(0xFF1B3534),
    secondaryContainer = Color(0xFF324B4A), onSecondaryContainer = Color(0xFFCCE8E6),
    tertiary = Color(0xFFB3C8E8), onTertiary = Color(0xFF1C314B),
    tertiaryContainer = Color(0xFF334863), onTertiaryContainer = Color(0xFFD3E4FF),
    inversePrimary = Color(0xFF006A68),
)

// --- Forest (green) ---
private val ForestLight = AccentRoles(
    primary = Color(0xFF386A20), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB7F397), onPrimaryContainer = Color(0xFF042100),
    secondary = Color(0xFF55624C), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD9E7CB), onSecondaryContainer = Color(0xFF131F0D),
    tertiary = Color(0xFF19686A), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBCEBED), onTertiaryContainer = Color(0xFF002021),
    inversePrimary = Color(0xFF9CD67E),
)
private val ForestDark = AccentRoles(
    primary = Color(0xFF9CD67E), onPrimary = Color(0xFF0C3900),
    primaryContainer = Color(0xFF205107), onPrimaryContainer = Color(0xFFB7F397),
    secondary = Color(0xFFBDCBB0), onSecondary = Color(0xFF283420),
    secondaryContainer = Color(0xFF3E4A35), onSecondaryContainer = Color(0xFFD9E7CB),
    tertiary = Color(0xFFA0CFD1), onTertiary = Color(0xFF003739),
    tertiaryContainer = Color(0xFF004F51), onTertiaryContainer = Color(0xFFBCEBED),
    inversePrimary = Color(0xFF386A20),
)

// --- Amber ---
private val AmberLight = AccentRoles(
    primary = Color(0xFF855400), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDDB3), onPrimaryContainer = Color(0xFF2A1800),
    secondary = Color(0xFF6F5B40), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFADEBC), onSecondaryContainer = Color(0xFF271904),
    tertiary = Color(0xFF51643F), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD4EABB), onTertiaryContainer = Color(0xFF102003),
    inversePrimary = Color(0xFFFFB95C),
)
private val AmberDark = AccentRoles(
    primary = Color(0xFFFFB95C), onPrimary = Color(0xFF472A00),
    primaryContainer = Color(0xFF653E00), onPrimaryContainer = Color(0xFFFFDDB3),
    secondary = Color(0xFFDDC2A1), onSecondary = Color(0xFF3E2D16),
    secondaryContainer = Color(0xFF56442B), onSecondaryContainer = Color(0xFFFADEBC),
    tertiary = Color(0xFFB8CEA1), onTertiary = Color(0xFF243515),
    tertiaryContainer = Color(0xFF3A4C2A), onTertiaryContainer = Color(0xFFD4EABB),
    inversePrimary = Color(0xFF855400),
)

// --- Rose ---
private val RoseLight = AccentRoles(
    primary = Color(0xFFB4255A), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E0), onPrimaryContainer = Color(0xFF3F0019),
    secondary = Color(0xFF75565C), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9E0), onSecondaryContainer = Color(0xFF2B151A),
    tertiary = Color(0xFF7C5635), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCC2), onTertiaryContainer = Color(0xFF2E1500),
    inversePrimary = Color(0xFFFFB1C2),
)
private val RoseDark = AccentRoles(
    primary = Color(0xFFFFB1C2), onPrimary = Color(0xFF66002C),
    primaryContainer = Color(0xFF900743), onPrimaryContainer = Color(0xFFFFD9E0),
    secondary = Color(0xFFE4BDC3), onSecondary = Color(0xFF43292E),
    secondaryContainer = Color(0xFF5B3F44), onSecondaryContainer = Color(0xFFFFD9E0),
    tertiary = Color(0xFFEFBD94), onTertiary = Color(0xFF48290B),
    tertiaryContainer = Color(0xFF623F20), onTertiaryContainer = Color(0xFFFFDCC2),
    inversePrimary = Color(0xFFB4255A),
)

/** The light scheme for [preset], neutral roles from [LightColors]. */
fun accentLightScheme(preset: AccentPreset): ColorScheme = LightColors.withAccent(
    when (preset) {
        AccentPreset.PLUM -> PlumLight
        AccentPreset.INDIGO -> IndigoLight
        AccentPreset.TEAL -> TealLight
        AccentPreset.FOREST -> ForestLight
        AccentPreset.AMBER -> AmberLight
        AccentPreset.ROSE -> RoseLight
    },
)

/** The dark scheme for [preset], neutral roles from [DarkColors]. */
fun accentDarkScheme(preset: AccentPreset): ColorScheme = DarkColors.withAccent(
    when (preset) {
        AccentPreset.PLUM -> PlumDark
        AccentPreset.INDIGO -> IndigoDark
        AccentPreset.TEAL -> TealDark
        AccentPreset.FOREST -> ForestDark
        AccentPreset.AMBER -> AmberDark
        AccentPreset.ROSE -> RoseDark
    },
)
