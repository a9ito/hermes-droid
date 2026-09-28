package com.a9ito.hermesagent.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.a9ito.hermesagent.ui.chat.ChatScreen
import com.a9ito.hermesagent.ui.sessions.SessionChatScreen
import com.a9ito.hermesagent.ui.sessions.SessionsScreen
import com.a9ito.hermesagent.ui.settings.SettingsScreen
import com.a9ito.hermesagent.ui.status.StatusScreen
import com.a9ito.hermesagent.ui.theme.HermesAgentTheme
import com.a9ito.hermesagent.ui.tools.ToolsScreen

/**
 * Root composable: applies the M3 Expressive theme, then hosts the top-level
 * screens behind a bottom navigation bar. Chat, Sessions, Tools and Status
 * self-gate on a saved host + token and offer a button that jumps to Settings.
 * The per-session chat ("session_chat/{id}") is a detail route reached from the
 * Sessions list, so it has no bottom-nav entry.
 */
@Composable
fun HermesAgentApp() {
    HermesAgentTheme {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = backStackEntry?.destination

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                NavigationBar {
                    Destination.entries.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(stringResource(destination.titleRes)) },
                        )
                    }
                }
            },
        ) { innerPadding ->
            val openSettings: () -> Unit = {
                navController.navigate(Destination.SETTINGS.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
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
                composable(Destination.TOOLS.route) {
                    ToolsScreen(onOpenSettings = openSettings)
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
