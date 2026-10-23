package com.networkanalyst.app.ui.screens.lanscan

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tv
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.networkanalyst.app.data.model.DiscoveryProtocol
import com.networkanalyst.app.data.model.LanDevice
import com.networkanalyst.app.ui.components.AdBannerComposable
import com.networkanalyst.app.ui.components.NetworkScanWarningDialog
import com.networkanalyst.app.ui.components.PermissionRationaleDialog
import com.networkanalyst.app.ui.components.PermissionType
import com.networkanalyst.app.ui.components.WarningPreferences
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanScanScreen(
    viewModel: LanScanViewModel
) {
    val progress by viewModel.scanProgress.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showPermissionDialog by remember { mutableStateOf(false) }
    var showWarningDialog by remember { mutableStateOf(false) }

    fun triggerScanFlow() {
        if (!WarningPreferences.isWarningAccepted(context)) {
            showWarningDialog = true
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            showPermissionDialog = true
        } else {
            viewModel.startScan(activity)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.startScan(activity)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quét mạng LAN", fontWeight = FontWeight.Bold) },
                actions = {
                    if (progress.devices.isNotEmpty()) {
                        IconButton(onClick = {
                            viewModel.saveResultsToHistory()
                            scope.launch {
                                snackbarHostState.showSnackbar("Đã lưu kết quả quét LAN vào Lịch sử!")
                            }
                        }) {
                            Icon(Icons.Default.Bookmark, contentDescription = "Lưu")
                        }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Control Card
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (progress.isScanning) "Đang dò tìm thiết bị..." else "Sẵn sàng quét",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "TCP probe + mDNS + NetBIOS UDP + SNMP",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = {
                                if (progress.isScanning) {
                                    viewModel.stopScan()
                                } else {
                                    triggerScanFlow()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (progress.isScanning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                if (progress.isScanning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (progress.isScanning) "Dừng" else "Bắt đầu")
                        }
                    }

                    if (progress.isScanning) {
                        Spacer(modifier = Modifier.height(12.dp))
                        val percent = if (progress.totalCount > 0) {
                            (progress.scannedCount.toFloat() / progress.totalCount.toFloat()).coerceIn(0f, 1f)
                        } else 0f

                        LinearProgressIndicator(
                            progress = { percent },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Đang quét: ${progress.currentIp ?: ""}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${progress.scannedCount}/${progress.totalCount} (${(percent * 100).toInt()}%)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Error display
            if (progress.errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = progress.errorMessage ?: "",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Device list header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Thiết bị tìm thấy (${progress.devices.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                if (progress.isScanning) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Discovered devices
            if (progress.devices.isEmpty() && !progress.isScanning) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nhấn 'Bắt đầu' để quét tất cả thiết bị trên mạng nội bộ.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(progress.devices, key = { it.ip }) { device ->
                        LanDeviceCard(device = device)
                    }
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    if (showWarningDialog) {
        NetworkScanWarningDialog(
            onDismiss = { showWarningDialog = false },
            onConfirmed = {
                showWarningDialog = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    showPermissionDialog = true
                } else {
                    viewModel.startScan(activity)
                }
            }
        )
    }

    if (showPermissionDialog) {
        PermissionRationaleDialog(
            permissionType = PermissionType.NEARBY_DEVICES,
            onDismiss = { showPermissionDialog = false },
            onRequestPermission = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.NEARBY_WIFI_DEVICES,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        )
                    )
                } else {
                    viewModel.startScan(activity)
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LanDeviceCard(device: LanDevice) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Icon according to device type
            val icon = getDeviceIcon(device)
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = device.ip,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (device.responseTimeMs != null) {
                        Text(
                            text = "${device.responseTimeMs} ms",
                            fontSize = 11.sp,
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                val displayName = device.hostname ?: device.vendor ?: "Thiết bị không xác định"
                Text(
                    text = displayName,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (device.mac != null) {
                    Text(
                        text = "MAC: ${device.mac}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Protocol & attribute badges
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (device.isCurrentDevice) {
                        DeviceBadge(text = "Máy này", color = Color(0xFF1976D2))
                    }
                    if (device.isGateway) {
                        DeviceBadge(text = "Gateway / Router", color = Color(0xFFE65100))
                    }
                    device.protocols.forEach { proto ->
                        val (label, col) = when (proto) {
                            DiscoveryProtocol.TCP_PROBE -> "TCP" to Color(0xFF455A64)
                            DiscoveryProtocol.MDNS -> "mDNS" to Color(0xFF00897B)
                            DiscoveryProtocol.NETBIOS -> "NetBIOS" to Color(0xFF6A1B9A)
                            DiscoveryProtocol.LLMNR -> "LLMNR" to Color(0xFF00838F)
                            DiscoveryProtocol.SNMP -> "SNMP" to Color(0xFFD84315)
                            DiscoveryProtocol.GATEWAY -> "Gateway" to Color(0xFFE65100)
                        }
                        DeviceBadge(text = label, color = col)
                    }

                    if (device.openPorts.isNotEmpty()) {
                        DeviceBadge(
                            text = "Cổng: ${device.openPorts.joinToString()}",
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceBadge(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

fun getDeviceIcon(device: LanDevice): ImageVector {
    val host = (device.hostname ?: "").lowercase()
    val vendor = (device.vendor ?: "").lowercase()

    return when {
        device.isGateway -> Icons.Default.Router
        host.contains("phone") || vendor.contains("phone") || vendor.contains("samsung") || vendor.contains("apple") && !host.contains("macbook") -> Icons.Default.PhoneAndroid
        host.contains("tv") || host.contains("chromecast") || vendor.contains("cast") -> Icons.Default.Tv
        host.contains("printer") || vendor.contains("printer") || device.openPorts.contains(9100) -> Icons.Default.Print
        else -> Icons.Default.Computer
    }
}
