package com.networkanalyst.app.ui.screens.lanscan

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.networkanalyst.app.data.local.HistoryDao
import com.networkanalyst.app.data.local.entity.HistoryEntity
import com.networkanalyst.app.data.model.ToolType
import com.networkanalyst.app.data.service.AdService
import com.networkanalyst.app.data.service.LanScanProgress
import com.networkanalyst.app.data.service.LanScannerService
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

    fun startScan(activity: Activity? = null) {
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

            val summary = "Tìm thấy ${devices.size} thiết bị trong mạng LAN"
            val details = buildString {
                appendLine("Tổng số thiết bị: ${devices.size}")
                devices.forEach { dev ->
                    appendLine("• ${dev.ip} - ${dev.hostname ?: dev.vendor ?: "Không rõ"} (MAC: ${dev.mac ?: "N/A"}) [${dev.protocols.joinToString()}]")
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
