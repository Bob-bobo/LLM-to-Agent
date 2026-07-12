package com.hermes.agent.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ChatBubble
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hermes.agent.core.common.adaptive.hermesWindowSize
import com.hermes.agent.core.common.adaptive.HermesWindowSize

/**
 * Top-level destinations for the app.
 */
enum class TopLevelDestination(
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val label: String
) {
    CHAT(
        route = Routes.CHAT,
        selectedIcon = Icons.Filled.ChatBubble,
        unselectedIcon = Icons.Outlined.ChatBubble,
        label = "对话"
    ),
    SETTINGS(
        route = Routes.SETTINGS,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        label = "设置"
    )
}

/**
 * Main app shell with adaptive navigation (bottom bar or rail).
 */
@Composable
fun HermesAppShell() {
    val navController = rememberNavController()
    val windowSize = hermesWindowSize()
    val useNavRail = windowSize != HermesWindowSize.COMPACT

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val destinations = TopLevelDestination.entries

    if (useNavRail) {
        // Navigation Rail (tablet / landscape)
        androidx.compose.foundation.layout.Row {
            NavigationRail {
                destinations.forEach { destination ->
                    NavigationRailItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            if (currentRoute != destination.route) {
                                navController.navigate(destination.route) {
                                    popUpTo(Routes.CHAT) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                if (currentRoute == destination.route)
                                    destination.selectedIcon
                                else
                                    destination.unselectedIcon,
                                contentDescription = destination.label
                            )
                        },
                        label = { Text(destination.label) }
                    )
                }
            }

            // Content
            HermesNavHost(
                navController = navController,
                modifier = Modifier.weight(1f)
            )
        }
    } else {
        // Bottom Navigation Bar (phone portrait)
        Scaffold(
            bottomBar = {
                NavigationBar {
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                if (currentRoute != destination.route) {
                                    navController.navigate(destination.route) {
                                        popUpTo(Routes.CHAT) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    if (currentRoute == destination.route)
                                        destination.selectedIcon
                                    else
                                        destination.unselectedIcon,
                                    contentDescription = destination.label
                                )
                            },
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            HermesNavHost(
                navController = navController,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
