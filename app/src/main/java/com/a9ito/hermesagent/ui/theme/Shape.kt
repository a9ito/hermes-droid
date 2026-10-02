package com.a9ito.hermesagent.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import com.a9ito.hermesagent.core.CornerStyle

/**
 * Material 3 Expressive shape scale.
 *
 * The Expressive system leans on rounder, larger corner radii than the classic
 * M3 baseline. [AppShapes] (the [CornerStyle.ROUNDED] set) is the app's default
 * and tracks the m3.material.io shape tokens; [shapesFor] lets the user pick a
 * sharper or rounder personality without touching individual components.
 */
val AppShapes: Shapes = shapesFor(CornerStyle.ROUNDED)

/** Build the M3 [Shapes] for a [CornerStyle]; ROUNDED reproduces the original tokens. */
fun shapesFor(style: CornerStyle): Shapes = when (style) {
    CornerStyle.SHARP -> Shapes(
        extraSmall = RoundedCornerShape(0.dp),
        small = RoundedCornerShape(2.dp),
        medium = RoundedCornerShape(4.dp),
        large = RoundedCornerShape(6.dp),
        extraLarge = RoundedCornerShape(10.dp),
    )
    CornerStyle.ROUNDED -> Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )
    CornerStyle.EXTRA -> Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(16.dp),
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(28.dp),
        extraLarge = RoundedCornerShape(36.dp),
    )
}
