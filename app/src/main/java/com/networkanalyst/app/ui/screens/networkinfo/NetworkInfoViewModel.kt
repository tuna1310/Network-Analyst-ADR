package com.networkanalyst.app.ui.screens.networkinfo

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.networkanalyst.app.data.local.HistoryDao
import com.networkanalyst.app.data.local.entity.HistoryEntity
import com.networkanalyst.app.data.model.NetworkInfoData
import com.networkanalyst.app.data.model.ToolType
import com.networkanalyst.app.data.service.AdService
import com.networkanalyst.app.data.service.NetworkDiagnosticsService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NetworkInfoViewModel @Inject constructor(
    private val diagnosticsService: NetworkDiagnosticsService,
    val adService: AdService,
    private val historyDao: HistoryDao
) : ViewModel() {

    val networkInfo: StateFlow<NetworkInfoData> = diagnosticsService.networkInfo

    fun refresh(activity: Activity? = null) {
        adService.recordToolAction(activity)
        diagnosticsService.refreshNetworkInfo()
    }

    fun saveSnapshotToHistory() {
        viewModelScope.launch {
            val current = networkInfo.value
            val summary = "${current.connectionType} | IP: ${current.localIpv4 ?: "N/A"} | Gateway: ${current.gateway ?: "N/A"}"
            val details = buildString {
                appendLine("Connection Type: ${current.connectionType}")
                current.ssid?.let { appendLine("SSID: $it") }
                current.bssid?.let { appendLine("BSSID: $it") }
                current.localIpv4?.let { appendLine("Local IPv4: $it / ${current.prefixLength}") }
                current.subnetMask?.let { appendLine("Subnet Mask: $it") }
                current.gateway?.let { appendLine("Default Gateway: $it") }
                current.dnsServers.takeIf { it.isNotEmpty() }?.let { appendLine("DNS: ${it.joinToString(", ")}") }
                current.publicIp?.let { appendLine("Public IP: $it (${current.ispName ?: "ISP"})") }
            }

            historyDao.insert(
                HistoryEntity(
                    toolType = ToolType.NETWORK_INFO,
                    target = current.localIpv4 ?: "Local Network",
                    summary = summary,
                    details = details
                )
            )
        }
    }
}
