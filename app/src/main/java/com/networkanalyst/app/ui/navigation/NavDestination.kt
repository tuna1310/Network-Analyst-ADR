package com.networkanalyst.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.ui.graphics.vector.ImageVector
import com.networkanalyst.app.R

sealed class Screen(val route: String, @StringRes val titleRes: Int, val icon: ImageVector) {
    object NetworkInfo : Screen("network_info", R.string.nav_network_info, Icons.Default.Router)
    object LanScan : Screen("lan_scan", R.string.nav_lan_scanner, Icons.Default.Lan)
    object Ping : Screen("ping", R.string.nav_ping, Icons.Default.Speed)
    object Traceroute : Screen("traceroute", R.string.nav_traceroute, Icons.Default.Timeline)
    object PortScan : Screen("port_scan", R.string.nav_port_scanner, Icons.Default.NetworkCheck)
    object Dns : Screen("dns", R.string.nav_dns, Icons.Default.Dns)
    object Whois : Screen("whois", R.string.nav_whois, Icons.Default.Public)
    object History : Screen("history", R.string.nav_history, Icons.Default.History)
}

val bottomNavScreens = listOf(
    Screen.NetworkInfo,
    Screen.LanScan,
    Screen.Ping,
    Screen.Traceroute,
    Screen.PortScan,
    Screen.Dns,
    Screen.Whois,
    Screen.History
)
