package com.akillikumanda.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.akillikumanda.app.core.model.DeviceCategory
import com.akillikumanda.app.feature.connections.ConnectionsScreen
import com.akillikumanda.app.feature.discovery.DiscoveryScreen
import com.akillikumanda.app.feature.home.HomeScreen
import com.akillikumanda.app.feature.remotes.RemoteHostScreen
import com.akillikumanda.app.feature.remotes.RemotesScreen
import com.akillikumanda.app.feature.settings.SettingsScreen

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route

    fun goTop(route: String) = nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        bottomBar = {
            if (TopLevel.entries.any { it.route == currentRoute }) {
                NavigationBar {
                    TopLevel.entries.forEach { t ->
                        NavigationBarItem(
                            selected = currentRoute == t.route,
                            onClick = { goTop(t.navRoute) },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label, maxLines = 1) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {
            composable(Routes.HOME) {
                HomeScreen(
                    onDiscover = { cat -> nav.navigate(Routes.discovery(cat?.name)) },
                    onOpenRemotes = { goTop(Routes.REMOTES) },
                    onOpenRemote = { nav.navigate(Routes.remote(it)) },
                    onOpenConnections = { nav.navigate(Routes.CONNECTIONS) },
                )
            }
            composable(Routes.REMOTES) {
                RemotesScreen(
                    onOpenRemote = { nav.navigate(Routes.remote(it)) },
                    onDiscover = { nav.navigate(Routes.discovery()) },
                )
            }
            composable(
                Routes.DISCOVERY,
                arguments = listOf(navArgument("category") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { e ->
                val cat = e.arguments?.getString("category")?.let { DeviceCategory.fromName(it) }
                DiscoveryScreen(
                    initialCategory = cat,
                    onOpenRemote = { id ->
                        nav.navigate(Routes.remote(id)) { popUpTo(Routes.HOME) }
                    },
                    onOpenConnections = { nav.navigate(Routes.CONNECTIONS) },
                    onClose = { if (!nav.popBackStack()) goTop(Routes.HOME) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onOpenConnections = { nav.navigate(Routes.CONNECTIONS) })
            }
            composable(Routes.REMOTE, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                RemoteHostScreen(
                    id = e.arguments?.getLong("id") ?: 0L,
                    onBack = { nav.popBackStack() },
                    onDiscover = { cat -> nav.navigate(Routes.discovery(cat.name)) },
                )
            }
            composable(Routes.CONNECTIONS) {
                ConnectionsScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}
