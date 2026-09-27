package com.a9ito.hermesagent.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive shape scale.
 *
 * The Expressive system leans on rounder, larger corner radii than the classic
 * M3 baseline. These values track the current m3.material.io shape tokens for
 * the standard shape roles. The extra "increased"/"extra-extra-large" roles are
 * provided by the library's own [Shapes] defaults; we override the seven core
 * roles here so cards, buttons, sheets, and dialogs read as Expressive.
 */
val AppShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
