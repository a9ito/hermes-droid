package com.a9ito.hermesagent.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Material 3 Expressive type ramp.
 *
 * We start from the M3 baseline [Typography] (which already carries the
 * Expressive metrics for M3 1.4.x) and override the display/headline roles with
 * the Expressive emphasis — slightly heavier weights and tighter tracking — so
 * top-level titles read with the "expressive" personality rather than the flat
 * baseline. Body/label roles keep the readable baseline metrics.
 *
 * Only a font family swap would be needed to rebrand; the role scale stays.
 */
private val DefaultFontFamily = FontFamily.Default

val AppTypography: Typography = Typography().run {
    copy(
        displayLarge = displayLarge.merge(
            TextStyle(fontFamily = DefaultFontFamily, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
        ),
        displayMedium = displayMedium.merge(
            TextStyle(fontFamily = DefaultFontFamily, fontWeight = FontWeight.SemiBold),
        ),
        displaySmall = displaySmall.merge(
            TextStyle(fontFamily = DefaultFontFamily, fontWeight = FontWeight.SemiBold),
        ),
        headlineLarge = headlineLarge.merge(
            TextStyle(fontFamily = DefaultFontFamily, fontWeight = FontWeight.SemiBold),
        ),
        headlineMedium = headlineMedium.merge(
            TextStyle(fontFamily = DefaultFontFamily, fontWeight = FontWeight.SemiBold),
        ),
        headlineSmall = headlineSmall.merge(
            TextStyle(fontFamily = DefaultFontFamily, fontWeight = FontWeight.Medium),
        ),
        titleLarge = titleLarge.merge(TextStyle(fontFamily = DefaultFontFamily, fontWeight = FontWeight.Medium)),
        titleMedium = titleMedium.merge(TextStyle(fontFamily = DefaultFontFamily, fontWeight = FontWeight.Medium)),
        bodyLarge = bodyLarge.merge(TextStyle(fontFamily = DefaultFontFamily)),
        bodyMedium = bodyMedium.merge(TextStyle(fontFamily = DefaultFontFamily)),
        labelLarge = labelLarge.merge(TextStyle(fontFamily = DefaultFontFamily, fontWeight = FontWeight.Medium)),
    )
}
