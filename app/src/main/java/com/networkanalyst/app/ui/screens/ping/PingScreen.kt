package com.networkanalyst.app.ui.screens.ping

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.networkanalyst.app.data.model.PingPacket
import com.networkanalyst.app.data.model.PingSummary
import com.networkanalyst.app.ui.components.AdBannerComposable
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PingScreen(
    viewModel: PingViewModel
) {
    val state by viewModel.pingState.collectAsState()
    val target by viewModel.targetHost.collectAsState()
    val count by viewModel.packetCount.collectAsState()

    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val gateway = remember { viewModel.getGatewayIp() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kiểm tra Ping (ICMP)", fontWeight = FontWeight.Bold) },
                actions = {
                    if (state.summary != null) {
                        IconButton(onClick = {
                            viewModel.saveResultsToHistory()
                            scope.launch {
                                snackbarHostState.showSnackbar("Đã lưu kết quả Ping vào Lịch sử!")
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
            // Target Input Card
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
                        label = { Text("Máy chủ hoặc IP đích") },
                        placeholder = { Text("google.com hoặc 8.8.8.8") },
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

                    Spacer(modifier = Modifier.height(8.dp))

                    // Preset chips
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PresetChip("8.8.8.8 (Google)", target == "8.8.8.8") { viewModel.setTarget("8.8.8.8") }
                        PresetChip("1.1.1.1 (Cloudflare)", target == "1.1.1.1") { viewModel.setTarget("1.1.1.1") }
                        PresetChip("google.com", target == "google.com") { viewModel.setTarget("google.com") }
                        if (gateway != null) {
                            PresetChip("Gateway ($gateway)", target == gateway) { viewModel.setTarget(gateway) }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Packet count selector & action button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Số gói:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.width(6.dp))
                            listOf(4, 10, 20).forEach { num ->
                                FilterChip(
                                    selected = count == num,
                                    onClick = { viewModel.setPacketCount(num) },
                                    label = { Text("$num", fontSize = 12.sp) },
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (state.isRunning) viewModel.stopPing() else viewModel.startPing(activity)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                if (state.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (state.isRunning) "Dừng" else "Ping")
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

            // Summary Card (if finished or available)
            if (state.summary != null) {
                PingSummaryCard(summary = state.summary!!)
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Live packets stream
            Text(
                text = "Các phản hồi (${state.packets.size}/$count)",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(state.packets, key = { it.sequence }) { packet ->
                    PingPacketRow(packet = packet)
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun PresetChip(text: String, isSelected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(text, fontSize = 11.sp) }
    )
}

@Composable
fun PingSummaryCard(summary: PingSummary) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Thống kê kết quả:",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Gửi: ${summary.transmitted}  |  Nhận: ${summary.received}  |  Mất: ${summary.lossPercentage.toInt()}%",
                    fontSize = 13.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Độ trễ: Min ${"%.1f".format(summary.minRttMs)}ms / TB ${"%.1f".format(summary.avgRttMs)}ms / Max ${"%.1f".format(summary.maxRttMs)}ms",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun PingPacketRow(packet: PingPacket) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (packet.isSuccess) Color(0xFF2E7D32) else Color(0xFFD32F2F)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (packet.isSuccess) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                if (packet.isSuccess) {
                    Text(
                        text = "#${packet.sequence}: ${packet.bytes} bytes từ ${packet.ip}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "ttl=${packet.ttl}  thời gian=${"%.1f".format(packet.rttMs)} ms",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "#${packet.sequence}: Hết thời gian chờ",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
