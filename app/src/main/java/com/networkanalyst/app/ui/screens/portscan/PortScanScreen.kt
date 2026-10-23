package com.networkanalyst.app.ui.screens.portscan

import android.app.Activity
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.networkanalyst.app.R
import com.networkanalyst.app.data.model.PortScanItem
import com.networkanalyst.app.ui.components.AdBannerComposable
import com.networkanalyst.app.ui.components.NetworkScanWarningDialog
import com.networkanalyst.app.ui.components.WarningPreferences
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PortScanScreen(
    viewModel: PortScanViewModel
) {
    val state by viewModel.scanState.collectAsState()
    val target by viewModel.targetHost.collectAsState()
    val mode by viewModel.mode.collectAsState()
    val customInput by viewModel.customPortsInput.collectAsState()
    val enableBannerGrab by viewModel.enableBannerGrab.collectAsState()

    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showWarningDialog by remember { mutableStateOf(false) }
    var showConfirmFullScanDialog by remember { mutableStateOf(false) }

    fun triggerScan() {
        if (!WarningPreferences.isWarningAccepted(context)) {
            showWarningDialog = true
            return
        }
        if (mode == PortScanMode.FULL_RANGE) {
            showConfirmFullScanDialog = true
            return
        }
        viewModel.startScan(activity)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_port_scanner), fontWeight = FontWeight.Bold) },
                actions = {
                    if (state.openPorts.isNotEmpty() || state.scannedCount > 0) {
                        IconButton(onClick = {
                            viewModel.saveResultsToHistory()
                            scope.launch {
                                snackbarHostState.showSnackbar("Đã lưu kết quả quét cổng vào Lịch sử!")
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
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = target,
                        onValueChange = { viewModel.setTarget(it) },
                        label = { Text("IP hoặc tên miền mục tiêu") },
                        placeholder = { Text("192.168.1.1 hoặc domain.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (target.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setTarget("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Xóa")
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Chế độ quét:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PortScanMode.values().forEach { m ->
                            val modeLabel = when (m) {
                                PortScanMode.COMMON -> stringResource(R.string.port_scan_mode_common)
                                PortScanMode.CUSTOM -> stringResource(R.string.port_scan_mode_custom)
                                PortScanMode.FULL_RANGE -> stringResource(R.string.port_scan_mode_full)
                            }
                            FilterChip(
                                selected = mode == m,
                                onClick = { viewModel.setMode(m) },
                                label = { Text(modeLabel, fontSize = 12.sp) }
                            )
                        }
                    }

                    if (mode == PortScanMode.CUSTOM) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customInput,
                            onValueChange = { viewModel.setCustomPortsInput(it) },
                            label = { Text("Danh sách cổng (Ví dụ: 80, 443, 8000-8080)") },
                            placeholder = { Text("80, 443, 8000-8080") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    // Ước tính thời gian khi quét dải lớn hoặc 1-65535
                    val estSeconds = remember(mode, customInput, target) { viewModel.getEstimatedDurationSeconds() }
                    val portCount = remember(mode, customInput) { viewModel.getTargetPorts().size }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$portCount cổng • Ước tính: ~$estSeconds giây (Đồng thời 200, Timeout thích ứng)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Banner Grab Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Đọc banner dịch vụ (Banner Grab)", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Text("Chỉ chạy khi có cổng mở (HTTP, SSH, FTP)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = enableBannerGrab,
                            onCheckedChange = { viewModel.setEnableBannerGrab(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Các nút điều khiển: Bắt đầu / Tạm dừng / Tiếp tục / Dừng
                    when {
                        state.isScanning -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.pauseScan() },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                                ) {
                                    Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.port_scan_pause))
                                }
                                Button(
                                    onClick = { viewModel.stopScan() },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.port_scan_stop))
                                }
                            }
                        }
                        state.isPaused -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.resumeScan(activity) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.port_scan_resume))
                                }
                                Button(
                                    onClick = { viewModel.stopScan() },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.port_scan_stop))
                                }
                            }
                        }
                        else -> {
                            Button(
                                onClick = { triggerScan() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.port_scan_start))
                            }
                        }
                    }

                    if (state.isScanning || state.isPaused) {
                        Spacer(modifier = Modifier.height(10.dp))
                        val percent = if (state.totalCount > 0) {
                            (state.scannedCount.toFloat() / state.totalCount.toFloat()).coerceIn(0f, 1f)
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
                                text = if (state.isPaused) "Đã tạm dừng: ${state.currentPort ?: ""}" else "Đang quét cổng: ${state.currentPort ?: ""}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${state.scannedCount}/${state.totalCount} (${(percent * 100).toInt()}%)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            if (state.errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.errorMessage ?: "",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Thống kê 4 trạng thái: Mở, Đóng, Bị chặn, Lỗi
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                StatusStatBadge(label = stringResource(R.string.port_state_open), count = state.openPorts.size, color = Color(0xFF2E7D32))
                StatusStatBadge(label = stringResource(R.string.port_state_closed), count = state.closedCount, color = Color(0xFFC62828))
                StatusStatBadge(label = stringResource(R.string.port_state_filtered), count = state.filteredCount, color = Color(0xFFEF6C00))
                if (state.errorCount > 0) {
                    StatusStatBadge(label = stringResource(R.string.port_state_error), count = state.errorCount, color = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cổng mở phát hiện (${state.openPorts.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                if (state.isScanning) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (state.openPorts.isEmpty() && !state.isScanning && !state.isPaused) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (state.scannedCount > 0) "Không phát hiện cổng mở nào trong dải vừa quét." else "Nhấn 'Bắt đầu quét cổng' để kiểm tra.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(state.openPorts, key = { it.port }) { item ->
                        PortRow(item = item)
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
                if (mode == PortScanMode.FULL_RANGE) {
                    showConfirmFullScanDialog = true
                } else {
                    viewModel.startScan(activity)
                }
            }
        )
    }

    if (showConfirmFullScanDialog) {
        val estSec = viewModel.getEstimatedDurationSeconds()
        AlertDialog(
            onDismissRequest = { showConfirmFullScanDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.port_scan_confirm_full_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.port_scan_confirm_full_message, estSec)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmFullScanDialog = false
                        viewModel.startScan(activity)
                    }
                ) {
                    Text(stringResource(R.string.port_scan_start))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showConfirmFullScanDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun StatusStatBadge(label: String, count: Int, color: Color) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$label: $count",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

@Composable
fun PortRow(item: PortScanItem) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2E7D32).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.LockOpen,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Cổng ${item.port}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    item.responseTimeMs?.let {
                        Text(
                            text = "${it}ms",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = item.serviceName,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )

                item.banner?.let { banner ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = banner,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }
        }
    }
}
