package com.a9ito.hermesagent.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Hand-picked static fallback palette for devices without dynamic color
 * (below Android 12) — a real Hermes-branded plum/violet scheme, NOT the
 * placeholder baseline purple. These values feed [lightColorScheme] /
 * [darkColorScheme] in Theme.kt when dynamic color is unavailable.
 *
 * Generated to be M3-tonal-consistent (primary/secondary/tertiary families
 * with matching on-/container tones) so the fallback still looks intentional.
 */

// --- Light ---
val md_primary_light = Color(0xFF5B4B8A)
val md_onPrimary_light = Color(0xFFFFFFFF)
val md_primaryContainer_light = Color(0xFFE6DEFF)
val md_onPrimaryContainer_light = Color(0xFF170F42)

val md_secondary_light = Color(0xFF605B71)
val md_onSecondary_light = Color(0xFFFFFFFF)
val md_secondaryContainer_light = Color(0xFFE6DFF9)
val md_onSecondaryContainer_light = Color(0xFF1C182B)

val md_tertiary_light = Color(0xFF7D5260)
val md_onTertiary_light = Color(0xFFFFFFFF)
val md_tertiaryContainer_light = Color(0xFFFFD8E4)
val md_onTertiaryContainer_light = Color(0xFF31111D)

val md_error_light = Color(0xFFBA1A1A)
val md_onError_light = Color(0xFFFFFFFF)
val md_errorContainer_light = Color(0xFFFFDAD6)
val md_onErrorContainer_light = Color(0xFF410002)

val md_background_light = Color(0xFFFCF8F8)
val md_onBackground_light = Color(0xFF1C1B1D)
val md_surface_light = Color(0xFFFCF8F8)
val md_onSurface_light = Color(0xFF1C1B1D)
val md_surfaceVariant_light = Color(0xFFE6E0EC)
val md_onSurfaceVariant_light = Color(0xFF48454E)
val md_outline_light = Color(0xFF79767F)
val md_outlineVariant_light = Color(0xFFC9C4D0)
val md_inverseSurface_light = Color(0xFF313033)
val md_inverseOnSurface_light = Color(0xFFF4EFF4)
val md_inversePrimary_light = Color(0xFFCBBEFF)
val md_surfaceTint_light = md_primary_light

// --- Dark ---
val md_primary_dark = Color(0xFFCBBEFF)
val md_onPrimary_dark = Color(0xFF2C2059)
val md_primaryContainer_dark = Color(0xFF433671)
val md_onPrimaryContainer_dark = Color(0xFFE6DEFF)

val md_secondary_dark = Color(0xFFCAC3DC)
val md_onSecondary_dark = Color(0xFF322D41)
val md_secondaryContainer_dark = Color(0xFF484458)
val md_onSecondaryContainer_dark = Color(0xFFE6DFF9)

val md_tertiary_dark = Color(0xFFEFB8C8)
val md_onTertiary_dark = Color(0xFF4A2532)
val md_tertiaryContainer_dark = Color(0xFF633B48)
val md_onTertiaryContainer_dark = Color(0xFFFFD8E4)

val md_error_dark = Color(0xFFFFB4AB)
val md_onError_dark = Color(0xFF690005)
val md_errorContainer_dark = Color(0xFF93000A)
val md_onErrorContainer_dark = Color(0xFFFFDAD6)

val md_background_dark = Color(0xFF141218)
val md_onBackground_dark = Color(0xFFE6E1E6)
val md_surface_dark = Color(0xFF141218)
val md_onSurface_dark = Color(0xFFE6E1E6)
val md_surfaceVariant_dark = Color(0xFF48454E)
val md_onSurfaceVariant_dark = Color(0xFFC9C4D0)
val md_outline_dark = Color(0xFF938F99)
val md_outlineVariant_dark = Color(0xFF48454E)
val md_inverseSurface_dark = Color(0xFFE6E1E6)
val md_inverseOnSurface_dark = Color(0xFF313033)
val md_inversePrimary_dark = md_primary_light
val md_surfaceTint_dark = md_primary_dark
