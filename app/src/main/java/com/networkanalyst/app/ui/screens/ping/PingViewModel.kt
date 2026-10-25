package com.networkanalyst.app.ui.screens.ping

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.networkanalyst.app.data.local.HistoryDao
import com.networkanalyst.app.data.local.entity.HistoryEntity
import com.networkanalyst.app.data.model.ToolType
import com.networkanalyst.app.data.service.AdService
import com.networkanalyst.app.data.service.DiagnosticsHelper
import com.networkanalyst.app.data.service.PingService
import com.networkanalyst.app.data.service.PingSessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PingViewModel @Inject constructor(
    private val pingService: PingService,
    val adService: AdService,
    private val historyDao: HistoryDao,
    private val diagnosticsHelper: DiagnosticsHelper
) : ViewModel() {

    val pingState: StateFlow<PingSessionState> = pingService.pingState

    private val _targetHost = MutableStateFlow("8.8.8.8")
    val targetHost: StateFlow<String> = _targetHost.asStateFlow()

    private val _packetCount = MutableStateFlow(4)
    val packetCount: StateFlow<Int> = _packetCount.asStateFlow()

    fun setTarget(host: String) {
        _targetHost.value = host
    }

    fun setPacketCount(count: Int) {
        _packetCount.value = count
    }

    fun getGatewayIp(): String? {
        return diagnosticsHelper.getNetworkInfoSnapshot().gateway
    }

    fun startPing(activity: Activity? = null) {
        adService.recordToolAction(activity)
        pingService.startPing(_targetHost.value, _packetCount.value)
    }

    fun stopPing() {
        pingService.stopPing()
    }

    fun saveResultsToHistory() {
        viewModelScope.launch {
            val state = pingState.value
            val summary = state.summary ?: return@launch

            val summaryText = "Ping ${summary.target}: ${summary.received}/${summary.transmitted} received (${summary.lossPercentage.toInt()}% loss), Avg: ${"%.1f".format(summary.avgRttMs)} ms"
            val details = buildString {
                appendLine("Target: ${summary.target} (${summary.ip ?: ""})")
                appendLine("Sent: ${summary.transmitted}, Received: ${summary.received}, Loss: ${summary.lossPercentage}%")
                appendLine("RTT Min/Avg/Max: ${"%.1f".format(summary.minRttMs)} / ${"%.1f".format(summary.avgRttMs)} / ${"%.1f".format(summary.maxRttMs)} ms")
                appendLine("\nPacket details:")
                state.packets.forEach { p ->
                    if (p.isSuccess) {
                        appendLine("#${p.sequence}: ${p.bytes} bytes from ${p.ip} ttl=${p.ttl} rtt=${"%.1f".format(p.rttMs)} ms")
                    } else {
                        appendLine("#${p.sequence}: Request timed out")
                    }
                }
            }

            historyDao.insert(
                HistoryEntity(
                    toolType = ToolType.PING,
                    target = summary.target,
                    summary = summaryText,
                    details = details
                )
            )
        }
    }
}
