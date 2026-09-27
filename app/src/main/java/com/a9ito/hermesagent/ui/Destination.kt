package com.a9ito.hermesagent.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.a9ito.hermesagent.R

/**
 * The three top-level destinations. [titleRes] feeds the bottom-nav label from
 * strings.xml (never hardcoded), so localization covers navigation too.
 *
 * Icons are drawn from androidx.compose.material:material-icons-core (the small,
 * always-bundled set). A dedicated chat-bubble glyph lives only in the large
 * material-icons-extended artifact, which we deliberately avoid; Email is the
 * closest messaging metaphor in core.
 */
enum class Destination(
    val route: String,
    val titleRes: Int,
    val icon: ImageVector,
) {
    CHAT("chat", R.string.nav_chat, Icons.Filled.Email),
    STATUS("status", R.string.nav_status, Icons.Filled.Info),
    SETTINGS("settings", R.string.nav_settings, Icons.Filled.Settings),
}
