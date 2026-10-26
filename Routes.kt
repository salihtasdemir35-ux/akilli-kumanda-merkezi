package com.akillikumanda.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val HOME = "home"
    const val REMOTES = "remotes"
    const val DISCOVERY = "discovery?category={category}"
    const val SETTINGS = "settings"
    const val REMOTE = "remote/{id}"
    const val CONNECTIONS = "connections"

    fun discovery(category: String? = null) = if (category == null) "discovery" else "discovery?category=$category"
    fun remote(id: Long) = "remote/$id"
}

enum class TopLevel(val route: String, val navRoute: String, val label: String, val icon: ImageVector) {
    HOME(Routes.HOME, Routes.HOME, "Ana Sayfa", Icons.Filled.Home),
    REMOTES(Routes.REMOTES, Routes.REMOTES, "Kumandalarım", Icons.Filled.SettingsRemote),
    DISCOVERY(Routes.DISCOVERY, "discovery", "Kumanda Bul", Icons.Filled.Search),
    SETTINGS(Routes.SETTINGS, Routes.SETTINGS, "Ayarlar", Icons.Filled.Settings),
}
