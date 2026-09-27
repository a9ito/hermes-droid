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
import com.a9ito.hermesagent.ui.chat.ChatScreen
import com.a9ito.hermesagent.ui.settings.SettingsScreen
import com.a9ito.hermesagent.ui.status.StatusScreen
import com.a9ito.hermesagent.ui.theme.HermesAgentTheme

/**
 * Root composable: applies the M3 Expressive theme, then hosts the three
 * screens behind a bottom navigation bar. Chat and Status self-gate on a saved
 * host + token and offer a button that jumps to Settings.
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
                composable(Destination.STATUS.route) {
                    StatusScreen(onOpenSettings = openSettings)
                }
                composable(Destination.SETTINGS.route) {
                    SettingsScreen()
                }
            }
        }
    }
}
