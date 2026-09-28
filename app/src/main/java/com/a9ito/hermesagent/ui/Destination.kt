package com.a9ito.hermesagent.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.a9ito.hermesagent.R

/**
 * The top-level destinations shown in the bottom navigation. [titleRes] feeds
 * the label from strings.xml (never hardcoded), so localization covers
 * navigation too.
 *
 * Icons are drawn from androidx.compose.material:material-icons-core (the small,
 * always-bundled set); the large material-icons-extended artifact is
 * deliberately avoided. Email is the closest messaging metaphor for Chat, List
 * for Sessions, Build for Tools, DateRange for scheduled Jobs, PlayArrow for
 * agent Runs.
 *
 * NOTE: at 7 destinations this bottom bar is over the Material 3 comfort range
 * (3-5). A follow-up should move secondary destinations behind an overflow or a
 * navigation drawer; tracked in the feature PRs.
 */
enum class Destination(
    val route: String,
    val titleRes: Int,
    val icon: ImageVector,
) {
    CHAT("chat", R.string.nav_chat, Icons.Filled.Email),
    SESSIONS("sessions", R.string.nav_sessions, Icons.AutoMirrored.Filled.List),
    RUNS("runs", R.string.nav_runs, Icons.Filled.PlayArrow),
    TOOLS("tools", R.string.nav_tools, Icons.Filled.Build),
    JOBS("jobs", R.string.nav_jobs, Icons.Filled.DateRange),
    STATUS("status", R.string.nav_status, Icons.Filled.Info),
    SETTINGS("settings", R.string.nav_settings, Icons.Filled.Settings),
}
