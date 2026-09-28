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
import com.a9ito.hermesagent.core.NavRoutes

/**
 * The app's top-level destinations. [titleRes] feeds the label from strings.xml
 * (never hardcoded), so localization covers navigation too.
 *
 * Icons are drawn from androidx.compose.material:material-icons-core (the small,
 * always-bundled set); the large material-icons-extended artifact is
 * deliberately avoided. Email is the closest messaging metaphor for Chat, List
 * for Sessions, PlayArrow for agent Runs, Build for Tools, DateRange for
 * scheduled Jobs.
 *
 * Layout split (see [PRIMARY] / [SECONDARY]): the bottom bar carries only the
 * three primary destinations plus a "More" entry, keeping it inside the
 * Material 3 comfort range (3-5). The secondary destinations live behind a
 * Google-Messages-style menu sheet ([NavMenuSheet]) opened from "More". Adding a
 * new destination is just adding it to one of the two lists.
 */
enum class Destination(
    val route: String,
    val titleRes: Int,
    val icon: ImageVector,
) {
    CHAT(NavRoutes.CHAT, R.string.nav_chat, Icons.Filled.Email),
    SESSIONS(NavRoutes.SESSIONS, R.string.nav_sessions, Icons.AutoMirrored.Filled.List),
    RUNS(NavRoutes.RUNS, R.string.nav_runs, Icons.Filled.PlayArrow),
    TOOLS(NavRoutes.TOOLS, R.string.nav_tools, Icons.Filled.Build),
    JOBS(NavRoutes.JOBS, R.string.nav_jobs, Icons.Filled.DateRange),
    STATUS(NavRoutes.STATUS, R.string.nav_status, Icons.Filled.Info),
    SETTINGS(NavRoutes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings);

    /** True when this destination lives behind the "More" menu, not the bottom bar. */
    val isSecondary: Boolean get() = NavRoutes.isSecondary(route)

    companion object {
        /** Destinations shown directly in the bottom navigation bar, in [NavRoutes.PRIMARY] order. */
        val PRIMARY: List<Destination> = NavRoutes.PRIMARY.mapNotNull(::fromRoute)

        /** Destinations reached through the "More" menu sheet, in [NavRoutes.SECONDARY] order. */
        val SECONDARY: List<Destination> = NavRoutes.SECONDARY.mapNotNull(::fromRoute)

        /** Resolve a nav route back to its destination, or null for detail/unknown routes. */
        fun fromRoute(route: String?): Destination? = entries.firstOrNull { it.route == route }

        /** True when [route] is one of the secondary destinations (so "More" is the active tab). */
        fun isSecondaryRoute(route: String?): Boolean = NavRoutes.isSecondary(route)
    }
}
