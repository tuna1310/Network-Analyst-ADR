package com.networkanalyst.app.ui.screens.networkinfo

import android.Manifest
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.networkanalyst.app.R
import com.networkanalyst.app.ui.components.AdBannerComposable
import com.networkanalyst.app.ui.components.PermissionRationaleDialog
import com.networkanalyst.app.ui.components.PermissionType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkInfoScreen(
    viewModel: NetworkInfoViewModel
) {
    val info by viewModel.networkInfo.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showPermissionRationale by remember { mutableStateOf(false) }
    var showPhoneStateRationale by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        viewModel.refresh(activity)
    }

    val phonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.refresh(activity)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Network Analyst",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.saveSnapshotToHistory()
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.netinfo_saved_history))
                        }
                    }) {
                        Icon(Icons.Default.Bookmark, contentDescription = stringResource(R.string.save))
                    }
                    IconButton(onClick = { viewModel.refresh(activity) }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            AdBannerComposable(adService = viewModel.adService)
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Permission Banner (if needed)
            if (info.requiresLocationPermission) {
                item {
                    PermissionNoticeCard(
                        onGrantClicked = { showPermissionRationale = true }
                    )
                }
            }

            // Connection Status Overview
            item {
                StatusOverviewCard(info = info)
            }

            // Wi-Fi Details
            if (info.isWifi) {
                item {
                    WifiDetailsCard(info = info)
                }
            }

            // Cellular & SIM Details
            item {
                CellularAndSimCard(
                    info = info,
                    onGrantPhonePermission = { showPhoneStateRationale = true }
                )
            }

            // IP & Routing Details
            item {
                IpRoutingCard(info = info)
            }

            // Public Internet Details
            item {
                PublicInternetCard(info = info, onRefresh = { viewModel.refresh(activity) })
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showPermissionRationale) {
        PermissionRationaleDialog(
            permissionType = PermissionType.LOCATION_FOR_WIFI,
            onDismiss = { showPermissionRationale = false },
            onRequestPermission = {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        )
    }

    if (showPhoneStateRationale) {
        PermissionRationaleDialog(
            permissionType = PermissionType.READ_PHONE_STATE,
            onDismiss = { showPhoneStateRationale = false },
            onRequestPermission = {
                phonePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
            }
        )
    }
}

@Composable
fun PermissionNoticeCard(onGrantClicked: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.netinfo_perm_location_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = stringResource(R.string.netinfo_perm_location_desc),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onGrantClicked,
                contentPadding = ButtonDefaults.TextButtonContentPadding
            ) {
                Text(stringResource(R.string.grant), fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun StatusOverviewCard(info: com.networkanalyst.app.data.model.NetworkInfoData) {
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (info.isConnected) Color(0xFF2E7D32) else Color(0xFFD32F2F))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (info.isConnected) stringResource(R.string.netinfo_connected) else stringResource(R.string.netinfo_disconnected),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                // Status Badges
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (info.isEthernet) {
                        BadgeChip(text = "Ethernet", icon = Icons.Default.Router, color = Color(0xFF00796B))
                    }
                    if (info.isVpn) {
                        BadgeChip(text = "VPN", icon = Icons.Default.VpnKey, color = Color(0xFF7B1FA2))
                    }
                    if (info.isHotspotActive) {
                        BadgeChip(text = "Hotspot", icon = Icons.Default.WifiTethering, color = Color(0xFFE65100))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = info.connectionType,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )

            if (info.isWifi && info.signalPercent != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.netinfo_signal_strength, info.signalPercent ?: 0, info.rssi ?: 0),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (info.signalPercent!!.toFloat() / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            }
        }
    }
}

@Composable
fun BadgeChip(text: String, icon: ImageVector, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun WifiDetailsCard(info: com.networkanalyst.app.data.model.NetworkInfoData) {
    SectionCard(title = stringResource(R.string.netinfo_wifi_details), icon = Icons.Default.Wifi) {
        InfoRow(label = stringResource(R.string.netinfo_ssid), value = info.ssid ?: stringResource(R.string.unknown))
        InfoRow(label = stringResource(R.string.netinfo_bssid), value = info.bssid ?: stringResource(R.string.unknown))
        InfoRow(label = stringResource(R.string.netinfo_wifi_standard), value = info.wifiStandard ?: "Wi-Fi")
        InfoRow(label = stringResource(R.string.netinfo_wifi_security), value = info.wifiSecurity ?: stringResource(R.string.unknown))
        InfoRow(label = stringResource(R.string.netinfo_frequency_channel), value = stringResource(R.string.netinfo_channel_format, info.frequencyMhz ?: 0, info.wifiChannel ?: 0))
        InfoRow(label = stringResource(R.string.netinfo_link_speed), value = stringResource(R.string.netinfo_speed_format, "${info.linkSpeedMbps ?: "?"}"))
    }
}

@Composable
fun CellularAndSimCard(
    info: com.networkanalyst.app.data.model.NetworkInfoData,
    onGrantPhonePermission: () -> Unit
) {
    SectionCard(title = stringResource(R.string.netinfo_cellular_details), icon = Icons.Default.PhoneAndroid) {
        InfoRow(label = stringResource(R.string.netinfo_cellular_tech), value = info.cellularNetworkType ?: stringResource(R.string.netinfo_no_cellular))

        Spacer(modifier = Modifier.height(6.dp))

        if (!info.simPermissionGranted) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.netinfo_sim_details_title),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = stringResource(R.string.netinfo_sim_perm_rationale),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = onGrantPhonePermission,
                        contentPadding = ButtonDefaults.TextButtonContentPadding
                    ) {
                        Text(stringResource(R.string.grant_permission), fontSize = 11.sp)
                    }
                }
            }
        } else {
            if (info.simInfoList.isEmpty()) {
                Text(
                    text = stringResource(R.string.netinfo_no_active_sim),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                info.simInfoList.forEach { sim ->
                    val roamingTag = if (sim.isDataRoaming) " [Roaming]" else ""
                    InfoRow(
                        label = stringResource(R.string.netinfo_sim_slot_format, sim.slotIndex + 1, sim.displayName),
                        value = "${sim.carrierName} (${sim.countryIso})$roamingTag"
                    )
                }
            }
        }
    }
}

@Composable
fun IpRoutingCard(info: com.networkanalyst.app.data.model.NetworkInfoData) {
    SectionCard(title = stringResource(R.string.netinfo_routing_details), icon = Icons.Default.Router) {
        InfoRow(label = stringResource(R.string.netinfo_local_ipv4), value = "${info.localIpv4 ?: "N/A"} / ${info.prefixLength ?: 24}")
        InfoRow(label = stringResource(R.string.netinfo_subnet_mask), value = info.subnetMask ?: "N/A")
        InfoRow(label = stringResource(R.string.netinfo_broadcast), value = info.broadcastAddress ?: "N/A")
        InfoRow(label = stringResource(R.string.netinfo_gateway), value = info.gateway ?: "N/A")
        InfoRow(label = stringResource(R.string.netinfo_interface), value = info.interfaceName ?: "N/A")
        if (info.localIpv6 != null) {
            InfoRow(label = stringResource(R.string.netinfo_local_ipv6), value = info.localIpv6)
        }
        if (info.dhcpServer != null) {
            InfoRow(label = stringResource(R.string.netinfo_dhcp_server), value = info.dhcpServer)
        }
        if (info.dnsServers.isNotEmpty()) {
            InfoRow(label = stringResource(R.string.netinfo_dns_servers), value = info.dnsServers.joinToString(", "))
        }
    }
}

@Composable
fun PublicInternetCard(
    info: com.networkanalyst.app.data.model.NetworkInfoData,
    onRefresh: () -> Unit
) {
    SectionCard(title = stringResource(R.string.netinfo_internet_details), icon = Icons.Default.Public) {
        if (info.isPublicIpLoading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.netinfo_loading_public_ip), fontSize = 13.sp)
            }
        } else {
            InfoRow(label = stringResource(R.string.netinfo_public_ipv4), value = info.publicIp ?: stringResource(R.string.netinfo_no_ipv4))
            InfoRow(label = stringResource(R.string.netinfo_public_ipv6), value = info.publicIpv6 ?: (info.publicIpv6Reason ?: stringResource(R.string.netinfo_no_ipv6)))
            InfoRow(label = stringResource(R.string.netinfo_isp), value = info.ispName ?: stringResource(R.string.unknown))
            InfoRow(label = stringResource(R.string.netinfo_location), value = info.publicLocation ?: stringResource(R.string.unknown))
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
