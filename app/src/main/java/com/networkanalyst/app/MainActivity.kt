package com.networkanalyst.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.networkanalyst.app.ui.navigation.Screen
import com.networkanalyst.app.ui.navigation.bottomNavScreens
import com.networkanalyst.app.ui.screens.dns.DnsScreen
import com.networkanalyst.app.ui.screens.dns.DnsViewModel
import com.networkanalyst.app.ui.screens.history.HistoryScreen
import com.networkanalyst.app.ui.screens.history.HistoryViewModel
import com.networkanalyst.app.ui.screens.lanscan.LanScanScreen
import com.networkanalyst.app.ui.screens.lanscan.LanScanViewModel
import com.networkanalyst.app.ui.screens.networkinfo.NetworkInfoScreen
import com.networkanalyst.app.ui.screens.networkinfo.NetworkInfoViewModel
import com.networkanalyst.app.ui.screens.ping.PingScreen
import com.networkanalyst.app.ui.screens.ping.PingViewModel
import com.networkanalyst.app.ui.screens.portscan.PortScanScreen
import com.networkanalyst.app.ui.screens.portscan.PortScanViewModel
import com.networkanalyst.app.ui.screens.traceroute.TracerouteScreen
import com.networkanalyst.app.ui.screens.traceroute.TracerouteViewModel
import com.networkanalyst.app.ui.theme.NetworkAnalystTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NetworkAnalystTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppNavigation()
                }
            }
        }
    }
}

@Composable
fun MainAppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                bottomNavScreens.forEach { screen ->
                    val isSelected = currentRoute == screen.route
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                screen.icon,
                                contentDescription = screen.title
                            )
                        },
                        label = {
                            Text(
                                text = screen.title,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.NetworkInfo.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.NetworkInfo.route) {
                val vm = hiltViewModel<NetworkInfoViewModel>()
                NetworkInfoScreen(viewModel = vm)
            }
            composable(Screen.LanScan.route) {
                val vm = hiltViewModel<LanScanViewModel>()
                LanScanScreen(viewModel = vm)
            }
            composable(Screen.Ping.route) {
                val vm = hiltViewModel<PingViewModel>()
                PingScreen(viewModel = vm)
            }
            composable(Screen.Traceroute.route) {
                val vm = hiltViewModel<TracerouteViewModel>()
                TracerouteScreen(viewModel = vm)
            }
            composable(Screen.PortScan.route) {
                val vm = hiltViewModel<PortScanViewModel>()
                PortScanScreen(viewModel = vm)
            }
            composable(Screen.Dns.route) {
                val vm = hiltViewModel<DnsViewModel>()
                DnsScreen(viewModel = vm)
            }
            composable(Screen.History.route) {
                val vm = hiltViewModel<HistoryViewModel>()
                HistoryScreen(viewModel = vm)
            }
        }
    }
}
