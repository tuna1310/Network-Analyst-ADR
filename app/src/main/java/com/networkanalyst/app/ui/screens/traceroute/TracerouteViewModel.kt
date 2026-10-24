package com.networkanalyst.app.ui.screens.traceroute

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.networkanalyst.app.data.local.HistoryDao
import com.networkanalyst.app.data.local.entity.HistoryEntity
import com.networkanalyst.app.data.model.ToolType
import com.networkanalyst.app.data.service.AdService
import com.networkanalyst.app.data.service.TracerouteService
import com.networkanalyst.app.data.service.TracerouteSessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TracerouteViewModel @Inject constructor(
    private val tracerouteService: TracerouteService,
    val adService: AdService,
    private val historyDao: HistoryDao
) : ViewModel() {

    val tracerouteState: StateFlow<TracerouteSessionState> = tracerouteService.tracerouteState

    private val _targetHost = MutableStateFlow("1.1.1.1")
    val targetHost: StateFlow<String> = _targetHost.asStateFlow()

    private val _enableGeoIp = MutableStateFlow(false)
    val enableGeoIp: StateFlow<Boolean> = _enableGeoIp.asStateFlow()

    fun setTarget(host: String) {
        _targetHost.value = host
    }

    fun setEnableGeoIp(enabled: Boolean) {
        _enableGeoIp.value = enabled
    }

    fun startTraceroute(activity: Activity? = null) {
        adService.recordToolAction(activity)
        tracerouteService.startTraceroute(
            host = _targetHost.value,
            enableGeoIp = _enableGeoIp.value
        )
    }

    fun stopTraceroute() {
        tracerouteService.stopTraceroute()
    }

    fun saveResultsToHistory() {
        viewModelScope.launch {
            val state = tracerouteState.value
            if (state.hops.isEmpty()) return@launch

            val lastHop = state.hops.lastOrNull()
            val reached = lastHop?.isDestination == true
            val summaryText = "Traceroute ${state.target}: ${state.hops.size} bước (Hops) - ${if (reached) "Đã tới đích" else "Dừng"}"
            val details = buildString {
                appendLine("Mục tiêu: ${state.target} (${state.resolvedIp ?: ""})")
                appendLine("Tổng số hop: ${state.hops.size}")
                state.hops.forEach { hop ->
                    val rtt = if (hop.rttMs != null) "${"%.1f".format(hop.rttMs)} ms" else "* * *"
                    appendLine("${hop.hop}. ${hop.ip} (${hop.hostname ?: ""}) - $rtt ${if (hop.isDestination) "[ĐÍCH]" else ""}")
                }
            }

            historyDao.insert(
                HistoryEntity(
                    toolType = ToolType.TRACEROUTE,
                    target = state.target,
                    summary = summaryText,
                    details = details
                )
            )
        }
    }
}
