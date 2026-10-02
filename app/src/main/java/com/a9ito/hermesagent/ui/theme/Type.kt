package com.a9ito.hermesagent.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.a9ito.hermesagent.core.FontChoice

/**
 * Material 3 Expressive type ramp.
 *
 * We start from the M3 baseline [Typography] (which already carries the
 * Expressive metrics for M3 1.4.x) and override the display/headline roles with
 * the Expressive emphasis (slightly heavier weights and tighter tracking) so
 * top-level titles read with the "expressive" personality rather than the flat
 * baseline. Body/label roles keep the readable baseline metrics.
 *
 * [AppTypography] is the app default (system sans-serif). [typographyFor] lets
 * the user swap the whole ramp to another built-in platform family (serif,
 * monospace) without shipping any font asset, so the type choice stays
 * dependency-free.
 */
private fun familyOf(choice: FontChoice): FontFamily = when (choice) {
    FontChoice.SYSTEM -> FontFamily.Default
    FontChoice.SERIF -> FontFamily.Serif
    FontChoice.MONOSPACE -> FontFamily.Monospace
}

/** Build the Expressive type ramp bound to [choice]'s font family. */
fun typographyFor(choice: FontChoice): Typography {
    val family = familyOf(choice)
    return Typography().run {
        copy(
            displayLarge = displayLarge.merge(
                TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
            ),
            displayMedium = displayMedium.merge(
                TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold),
            ),
            displaySmall = displaySmall.merge(
                TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold),
            ),
            headlineLarge = headlineLarge.merge(
                TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold),
            ),
            headlineMedium = headlineMedium.merge(
                TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold),
            ),
            headlineSmall = headlineSmall.merge(
                TextStyle(fontFamily = family, fontWeight = FontWeight.Medium),
            ),
            titleLarge = titleLarge.merge(TextStyle(fontFamily = family, fontWeight = FontWeight.Medium)),
            titleMedium = titleMedium.merge(TextStyle(fontFamily = family, fontWeight = FontWeight.Medium)),
            titleSmall = titleSmall.merge(TextStyle(fontFamily = family, fontWeight = FontWeight.Medium)),
            bodyLarge = bodyLarge.merge(TextStyle(fontFamily = family)),
            bodyMedium = bodyMedium.merge(TextStyle(fontFamily = family)),
            bodySmall = bodySmall.merge(TextStyle(fontFamily = family)),
            labelLarge = labelLarge.merge(TextStyle(fontFamily = family, fontWeight = FontWeight.Medium)),
            labelMedium = labelMedium.merge(TextStyle(fontFamily = family, fontWeight = FontWeight.Medium)),
            labelSmall = labelSmall.merge(TextStyle(fontFamily = family, fontWeight = FontWeight.Medium)),
        )
    }
}

val AppTypography: Typography = typographyFor(FontChoice.SYSTEM)
