package com.networkanalyst.app.ui.screens.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.networkanalyst.app.ui.screens.dns.DnsScreen
import com.networkanalyst.app.ui.screens.dns.DnsViewModel
import com.networkanalyst.app.ui.screens.ping.PingScreen
import com.networkanalyst.app.ui.screens.ping.PingViewModel
import com.networkanalyst.app.ui.screens.portscan.PortScanScreen
import com.networkanalyst.app.ui.screens.portscan.PortScanViewModel
import com.networkanalyst.app.ui.screens.traceroute.TracerouteScreen
import com.networkanalyst.app.ui.screens.traceroute.TracerouteViewModel
import com.networkanalyst.app.ui.screens.whois.WhoisScreen
import com.networkanalyst.app.ui.screens.whois.WhoisViewModel

@Composable
fun ToolsScreen() {
    val tabs = listOf("Ping", "Traceroute", "Port Scan", "DNS", "Whois")
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 12.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    height = 3.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selectedTab) {
                0 -> {
                    val vm = hiltViewModel<PingViewModel>()
                    PingScreen(viewModel = vm)
                }
                1 -> {
                    val vm = hiltViewModel<TracerouteViewModel>()
                    TracerouteScreen(viewModel = vm)
                }
                2 -> {
                    val vm = hiltViewModel<PortScanViewModel>()
                    PortScanScreen(viewModel = vm)
                }
                3 -> {
                    val vm = hiltViewModel<DnsViewModel>()
                    DnsScreen(viewModel = vm)
                }
                4 -> {
                    val vm = hiltViewModel<WhoisViewModel>()
                    WhoisScreen(viewModel = vm)
                }
            }
        }
    }
}
