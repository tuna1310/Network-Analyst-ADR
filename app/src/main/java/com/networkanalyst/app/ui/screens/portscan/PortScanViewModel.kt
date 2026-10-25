package com.networkanalyst.app.ui.screens.portscan

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.networkanalyst.app.data.local.HistoryDao
import com.networkanalyst.app.data.local.entity.HistoryEntity
import com.networkanalyst.app.data.model.ToolType
import com.networkanalyst.app.data.service.AdService
import com.networkanalyst.app.data.service.DiagnosticsHelper
import com.networkanalyst.app.data.service.PortRangeParser
import com.networkanalyst.app.data.service.PortScanProgress
import com.networkanalyst.app.data.service.PortScannerService
import com.networkanalyst.app.ui.components.ScanPolicyHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PortScanMode(val displayName: String) {
    COMMON("Common"),
    CUSTOM("Custom"),
    FULL_RANGE("1 - 65535")
}

@HiltViewModel
class PortScanViewModel @Inject constructor(
    private val portScannerService: PortScannerService,
    val adService: AdService,
    private val historyDao: HistoryDao,
    private val diagnosticsHelper: DiagnosticsHelper
) : ViewModel() {

    val scanState: StateFlow<PortScanProgress> = portScannerService.scanState

    private val _targetHost = MutableStateFlow("192.168.1.1")
    val targetHost: StateFlow<String> = _targetHost.asStateFlow()

    private val _mode = MutableStateFlow(PortScanMode.COMMON)
    val mode: StateFlow<PortScanMode> = _mode.asStateFlow()

    private val _customPortsInput = MutableStateFlow("80, 443, 8080, 8443")
    val customPortsInput: StateFlow<String> = _customPortsInput.asStateFlow()

    private val _concurrencyLimit = MutableStateFlow(200)
    val concurrencyLimit: StateFlow<Int> = _concurrencyLimit.asStateFlow()

    private val _enableBannerGrab = MutableStateFlow(false)
    val enableBannerGrab: StateFlow<Boolean> = _enableBannerGrab.asStateFlow()

    init {
        val gateway = diagnosticsHelper.getNetworkInfoSnapshot().gateway
        if (gateway != null) {
            _targetHost.value = gateway
        }
    }

    fun setTarget(host: String) {
        _targetHost.value = host
    }

    fun setMode(m: PortScanMode) {
        _mode.value = m
    }

    fun setCustomPortsInput(input: String) {
        _customPortsInput.value = input
    }

    fun setConcurrency(limit: Int) {
        _concurrencyLimit.value = limit.coerceIn(10, 300)
    }

    fun setEnableBannerGrab(enabled: Boolean) {
        _enableBannerGrab.value = enabled
    }

    fun getTargetPorts(): List<Int> {
        return when (_mode.value) {
            PortScanMode.COMMON -> PortScannerService.COMMON_PORTS
            PortScanMode.CUSTOM -> PortRangeParser.parsePorts(_customPortsInput.value).ifEmpty { listOf(80, 443) }
            PortScanMode.FULL_RANGE -> (1..65535).toList()
        }
    }

    fun getEstimatedDurationSeconds(): Int {
        val count = getTargetPorts().size
        val isLan = PortRangeParser.isPrivateIp(_targetHost.value)
        val timeoutMs = if (isLan) 200 else 500
        return PortRangeParser.estimateDurationSeconds(count, _concurrencyLimit.value, timeoutMs)
    }

    fun startScan(activity: Activity? = null, context: Context? = null) {
        // Chặn quét nếu người dùng từ chối hoặc chưa đồng ý chính sách ủy quyền mạng
        if (context != null && !ScanPolicyHelper.hasAgreedToScanPolicy(context)) {
            return
        }
        adService.recordToolAction(activity)
        val ports = getTargetPorts()

        portScannerService.startScan(
            host = _targetHost.value,
            ports = ports,
            concurrencyLimit = _concurrencyLimit.value,
            customTimeoutMs = null, // Adaptive LAN/WAN timeout
            enableBannerGrab = _enableBannerGrab.value
        )
    }

    fun stopScan() {
        portScannerService.stopScan()
    }

    fun pauseScan() {
        portScannerService.pauseScan()
    }

    fun resumeScan(activity: Activity? = null) {
        adService.recordToolAction(activity)
        portScannerService.resumeScan(enableBannerGrab = _enableBannerGrab.value)
    }

    fun saveResultsToHistory() {
        viewModelScope.launch {
            val state = scanState.value
            val summaryText = "Port scan ${state.target}: ${state.openPorts.size} open, ${state.closedCount} closed, ${state.filteredCount} filtered / ${state.scannedCount} scanned"
            val details = buildString {
                appendLine("Target: ${state.target} (${state.resolvedIp ?: ""})")
                appendLine("Mode: ${_mode.value.displayName}")
                appendLine("Concurrency: ${state.concurrencyUsed} | Timeout: ${state.timeoutUsedMs}ms")
                appendLine("Scanned ports: ${state.scannedCount}/${state.totalCount}")
                appendLine("Stats: Open: ${state.openPorts.size} | Closed: ${state.closedCount} | Filtered: ${state.filteredCount}")
                appendLine("\nOpen ports:")
                state.openPorts.forEach { p ->
                    appendLine("• Port ${p.port} (${p.serviceName}) - ${p.responseTimeMs ?: 0}ms ${if (p.banner != null) "[Banner: ${p.banner}]" else ""}")
                }
            }

            historyDao.insert(
                HistoryEntity(
                    toolType = ToolType.PORT_SCAN,
                    target = state.target,
                    summary = summaryText,
                    details = details
                )
            )
        }
    }
}
