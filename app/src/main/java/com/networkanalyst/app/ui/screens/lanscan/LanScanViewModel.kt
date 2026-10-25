package com.networkanalyst.app.ui.screens.lanscan

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.networkanalyst.app.data.local.HistoryDao
import com.networkanalyst.app.data.local.entity.HistoryEntity
import com.networkanalyst.app.data.model.ToolType
import com.networkanalyst.app.data.service.AdService
import com.networkanalyst.app.data.service.LanScanProgress
import com.networkanalyst.app.data.service.LanScannerService
import com.networkanalyst.app.ui.components.ScanPolicyHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LanScanViewModel @Inject constructor(
    private val scannerService: LanScannerService,
    val adService: AdService,
    private val historyDao: HistoryDao
) : ViewModel() {

    val scanProgress: StateFlow<LanScanProgress> = scannerService.scanProgress

    fun startScan(activity: Activity? = null, context: Context? = null) {
        // Chặn quét nếu người dùng từ chối hoặc chưa đồng ý chính sách ủy quyền mạng
        if (context != null && !ScanPolicyHelper.hasAgreedToScanPolicy(context)) {
            return
        }
        adService.recordToolAction(activity)
        scannerService.startScan()
    }

    fun stopScan() {
        scannerService.stopScan()
    }

    fun saveResultsToHistory() {
        viewModelScope.launch {
            val progress = scanProgress.value
            val devices = progress.devices
            if (devices.isEmpty()) return@launch

            val summary = "Discovered ${devices.size} devices on LAN"
            val details = buildString {
                appendLine("Total devices: ${devices.size}")
                devices.forEach { dev ->
                    appendLine("• ${dev.ip} - ${dev.hostname ?: dev.vendor ?: "Unknown"} (MAC: ${dev.mac ?: "N/A"}) [${dev.protocols.joinToString()}]")
                }
            }

            historyDao.insert(
                HistoryEntity(
                    toolType = ToolType.LAN_SCAN,
                    target = "Subnet LAN",
                    summary = summary,
                    details = details
                )
            )
        }
    }
}
