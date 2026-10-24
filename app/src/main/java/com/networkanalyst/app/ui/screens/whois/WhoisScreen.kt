package com.networkanalyst.app.ui.screens.whois

import android.app.Activity
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.networkanalyst.app.R
import com.networkanalyst.app.data.model.WhoisResult
import com.networkanalyst.app.data.model.WhoisSource
import com.networkanalyst.app.ui.components.AdBannerComposable
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WhoisScreen(
    viewModel: WhoisViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_whois), fontWeight = FontWeight.Bold) },
                actions = {
                    if (uiState.result != null) {
                        IconButton(onClick = {
                            viewModel.saveResultToHistory()
                            scope.launch {
                                snackbarHostState.showSnackbar("Đã lưu kết quả Whois vào Lịch sử!")
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
                        value = uiState.target,
                        onValueChange = { viewModel.setTarget(it) },
                        label = { Text("Tên miền, Địa chỉ IP hoặc ASN") },
                        placeholder = { Text("google.com, 1.1.1.1, AS15169") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (uiState.target.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setTarget("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Xóa")
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = uiState.target == "google.com",
                            onClick = { viewModel.setTarget("google.com") },
                            label = { Text("google.com", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = uiState.target == "1.1.1.1",
                            onClick = { viewModel.setTarget("1.1.1.1") },
                            label = { Text("1.1.1.1", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = uiState.target == "AS15169",
                            onClick = { viewModel.setTarget("AS15169") },
                            label = { Text("AS15169 (Google)", fontSize = 11.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.queryWhois(activity) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isLoading
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Đang tra cứu...")
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.whois_query_button))
                        }
                    }
                }
            }

            if (uiState.errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.errorMessage ?: "",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            val res = uiState.result
            if (res != null) {
                TabRow(
                    selectedTabIndex = uiState.selectedTab,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = uiState.selectedTab == 0,
                        onClick = { viewModel.setSelectedTab(0) },
                        text = { Text(stringResource(R.string.whois_tab_summary)) }
                    )
                    Tab(
                        selected = uiState.selectedTab == 1,
                        onClick = { viewModel.setSelectedTab(1) },
                        text = { Text(stringResource(R.string.whois_tab_raw)) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (uiState.selectedTab == 0) {
                    WhoisSummaryView(result = res)
                } else {
                    WhoisRawView(
                        result = res,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(res.rawContent))
                            scope.launch {
                                snackbarHostState.showSnackbar("Đã sao chép vào bộ nhớ tạm!")
                            }
                        }
                    )
                }
            } else if (!uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nhập tên miền, IP hoặc ASN để tra cứu thông tin Whois / RDAP.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WhoisSummaryView(result: WhoisResult) {
    val summary = result.summary
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Source Badge & Query time
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val sourceText = when (result.source) {
                WhoisSource.RDAP_IANA -> "RDAP (IANA Bootstrap)"
                WhoisSource.RDAP_ORG_FALLBACK -> "RDAP (rdap.org)"
                WhoisSource.WHOIS_PORT_43 -> "Whois (TCP Port 43)"
            }
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = sourceText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Text(
                text = "${result.queryTimeMs} ms",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF2E7D32)
            )
        }

        // Entity / Domain
        summary.entityName?.let {
            WhoisInfoCard(label = "Chủ sở hữu / Thực thể", value = it)
        }

        // Registrar
        summary.registrar?.let {
            WhoisInfoCard(label = "Nhà đăng ký (Registrar)", value = it)
        }

        // Dates
        if (summary.createdDate != null || summary.expiresDate != null || summary.updatedDate != null) {
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Thông tin thời gian", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    summary.createdDate?.let { Text("• Ngày tạo: $it", fontSize = 13.sp) }
                    summary.updatedDate?.let { Text("• Ngày cập nhật: $it", fontSize = 13.sp) }
                    summary.expiresDate?.let { Text("• Ngày hết hạn: $it", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }

        // CIDR / IP Range / ASN
        summary.cidr?.let {
            WhoisInfoCard(label = "Dải mạng (CIDR)", value = it)
        }
        summary.asnNumber?.let {
            WhoisInfoCard(label = "ASN", value = "$it (${summary.asnName ?: ""})")
        }

        // Statuses
        if (summary.statuses.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Trạng thái", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        summary.statuses.forEach { st ->
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(4.dp)) {
                                Text(st, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                }
            }
        }

        // Nameservers
        if (summary.nameservers.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Máy chủ tên miền (Nameservers)", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    summary.nameservers.forEach { ns ->
                        Text("• $ns", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        // Contact Email
        summary.contactEmail?.let {
            WhoisInfoCard(label = "Email liên hệ / Lạm dụng", value = it)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun WhoisInfoCard(label: String, value: String) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun WhoisRawView(
    result: WhoisResult,
    onCopy: () -> Unit
) {
    val scrollState = rememberScrollState()
    val hScrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Dung lượng: ${result.rawContent.length} bytes",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.whois_copy_raw), fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Card(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .verticalScroll(scrollState)
                    .horizontalScroll(hScrollState)
            ) {
                Text(
                    text = result.rawContent,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}
