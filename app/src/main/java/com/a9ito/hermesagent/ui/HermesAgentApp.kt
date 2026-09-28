package com.a9ito.hermesagent.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.a9ito.hermesagent.ui.chat.ChatScreen
import com.a9ito.hermesagent.ui.jobs.JobsScreen
import com.a9ito.hermesagent.ui.runs.RunsScreen
import com.a9ito.hermesagent.ui.sessions.SessionChatScreen
import com.a9ito.hermesagent.ui.sessions.SessionsScreen
import com.a9ito.hermesagent.ui.settings.SettingsScreen
import com.a9ito.hermesagent.ui.status.StatusScreen
import com.a9ito.hermesagent.ui.theme.HermesAgentTheme
import com.a9ito.hermesagent.ui.tools.ToolsScreen

/**
 * Root composable: applies the M3 Expressive theme, then hosts the top-level
 * screens behind a bottom navigation bar.
 *
 * The bottom bar carries only [Destination.PRIMARY] (Chat, Sessions, Runs) plus
 * a "More" item — keeping it inside the Material 3 comfort range (3-5). "More"
 * opens a Google-Messages-style menu sheet ([NavMenuSheet]) listing the
 * [Destination.SECONDARY] destinations (Tools, Jobs, Status, Settings). "More"
 * shows as the active tab whenever a secondary destination is on screen, so the
 * bar always reflects where you are.
 *
 * Chat, Sessions, Tools and Status self-gate on a saved host + token and offer a
 * button that jumps to Settings. The per-session chat ("session_chat/{id}") is a
 * detail route reached from the Sessions list, so it has no nav entry.
 */
@Composable
fun HermesAgentApp() {
    HermesAgentTheme {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = backStackEntry?.destination
        val currentRoute = currentDestination?.route
        var showMenuSheet by remember { mutableStateOf(false) }

        val navigateTo: (Destination) -> Unit = { destination ->
            navController.navigate(destination.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }

        if (showMenuSheet) {
            NavMenuSheet(
                destinations = Destination.SECONDARY,
                currentRoute = currentRoute,
                onSelect = { destination ->
                    showMenuSheet = false
                    navigateTo(destination)
                },
                onDismiss = { showMenuSheet = false },
            )
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                NavigationBar {
                    // Primary destinations: real nav items.
                    Destination.PRIMARY.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateTo(destination) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(stringResource(destination.titleRes)) },
                        )
                    }
                    // "More": opens the menu sheet; active whenever a secondary
                    // destination is the current screen.
                    NavigationBarItem(
                        selected = Destination.isSecondaryRoute(currentRoute),
                        onClick = { showMenuSheet = true },
                        icon = { Icon(Icons.Filled.Menu, contentDescription = null) },
                        label = { Text(stringResource(com.a9ito.hermesagent.R.string.nav_more)) },
                    )
                }
            },
        ) { innerPadding ->
            val openSettings: () -> Unit = { navigateTo(Destination.SETTINGS) }
            NavHost(
                navController = navController,
                startDestination = Destination.CHAT.route,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable(Destination.CHAT.route) {
                    ChatScreen(onOpenSettings = openSettings)
                }
                composable(Destination.SESSIONS.route) {
                    SessionsScreen(
                        onOpenSettings = openSettings,
                        onOpenSession = { id -> navController.navigate("session_chat/$id") },
                    )
                }
                composable(Destination.RUNS.route) {
                    RunsScreen(onOpenSettings = openSettings)
                }
                composable(Destination.TOOLS.route) {
                    ToolsScreen(onOpenSettings = openSettings)
                }
                composable(Destination.JOBS.route) {
                    JobsScreen(onOpenSettings = openSettings)
                }
                composable(Destination.STATUS.route) {
                    StatusScreen(onOpenSettings = openSettings)
                }
                composable(Destination.SETTINGS.route) {
                    SettingsScreen()
                }
                composable(
                    route = "session_chat/{sessionId}",
                    arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
                ) { entry ->
                    val sessionId = entry.arguments?.getString("sessionId").orEmpty()
                    SessionChatScreen(
                        sessionId = sessionId,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
