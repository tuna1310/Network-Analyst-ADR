package com.networkanalyst.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object NetworkInfo : Screen("network_info", "Mạng", Icons.Default.Router)
    object LanScan : Screen("lan_scan", "Quét LAN", Icons.Default.Lan)
    object Ping : Screen("ping", "Ping", Icons.Default.Speed)
    object Traceroute : Screen("traceroute", "Trace", Icons.Default.Timeline)
    object PortScan : Screen("port_scan", "Cổng", Icons.Default.NetworkCheck)
    object Dns : Screen("dns", "DNS", Icons.Default.Dns)
    object History : Screen("history", "Lịch sử", Icons.Default.History)
}

val bottomNavScreens = listOf(
    Screen.NetworkInfo,
    Screen.LanScan,
    Screen.Ping,
    Screen.Traceroute,
    Screen.PortScan,
    Screen.Dns,
    Screen.History
)
