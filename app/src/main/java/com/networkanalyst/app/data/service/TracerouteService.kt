package com.networkanalyst.app.data.service

import com.networkanalyst.app.data.model.TracerouteHop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class TracerouteSessionState(
    val isRunning: Boolean = false,
    val target: String = "",
    val resolvedIp: String? = null,
    val currentHop: Int = 0,
    val maxHops: Int = 30,
    val hops: List<TracerouteHop> = emptyList(),
    val errorMessage: String? = null
)

@Singleton
class TracerouteService @Inject constructor(
    private val geoIpService: GeoIpService
) {
    private val _tracerouteState = MutableStateFlow(TracerouteSessionState())
    val tracerouteState: StateFlow<TracerouteSessionState> = _tracerouteState.asStateFlow()

    private var traceJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun startTraceroute(
        host: String,
        maxHops: Int = 30,
        enableGeoIp: Boolean = false
    ) {
        stopTraceroute()
        geoIpService.resetSession()

        traceJob = scope.launch {
            val cleanHost = host.trim()
            if (cleanHost.isEmpty()) {
                _tracerouteState.value = TracerouteSessionState(
                    errorMessage = "Vui lòng nhập địa chỉ IP hoặc tên miền hợp lệ."
                )
                return@launch
            }

            _tracerouteState.value = TracerouteSessionState(
                isRunning = true,
                target = cleanHost,
                maxHops = maxHops
            )

            try {
                val targetInet = withContext(Dispatchers.IO) {
                    try {
                        InetAddress.getByName(cleanHost)
                    } catch (e: Exception) {
                        null
                    }
                }

                if (targetInet == null) {
                    _tracerouteState.value = TracerouteSessionState(
                        isRunning = false,
                        target = cleanHost,
                        errorMessage = "Không thể phân giải tên miền: $cleanHost"
                    )
                    return@launch
                }

                val targetIp = targetInet.hostAddress ?: cleanHost
                _tracerouteState.value = _tracerouteState.value.copy(resolvedIp = targetIp)

                val hopsList = mutableListOf<TracerouteHop>()
                var consecutiveTimeouts = 0

                val pingExecutable = when {
                    java.io.File("/system/bin/ping").exists() -> "/system/bin/ping"
                    else -> "ping"
                }

                for (ttl in 1..maxHops) {
                    if (!isActive) break

                    _tracerouteState.value = _tracerouteState.value.copy(currentHop = ttl)

                    val startHopTime = System.currentTimeMillis()
                    val outputLines = mutableListOf<String>()

                    withTimeoutOrNull(2500L) {
                        var process: Process? = null
                        try {
                            val pb = ProcessBuilder(
                                pingExecutable,
                                "-c", "1",
                                "-W", "2",
                                "-t", ttl.toString(),
                                cleanHost
                            )
                            pb.redirectErrorStream(true)
                            process = pb.start()

                            val reader = BufferedReader(InputStreamReader(process.inputStream))
                            var line: String? = null

                            while (isActive && reader.readLine().also { line = it } != null) {
                                line?.let { outputLines.add(it) }
                            }

                            process.waitFor(2, TimeUnit.SECONDS)
                        } catch (_: Exception) {
                        } finally {
                            try {
                                process?.destroyForcibly()
                            } catch (_: Exception) {}
                        }
                    }

                    if (TracerouteOutputParser.isTtlFlagUnsupported(outputLines)) {
                        _tracerouteState.value = _tracerouteState.value.copy(
                            isRunning = false,
                            errorMessage = "Lệnh ping trên thiết bị này không hỗ trợ cờ '-t' (TTL). Các nhà sản xuất (OEM) có thể dùng các cờ khác hoặc hạn chế traceroute khi không có quyền root."
                        )
                        return@launch
                    }

                    val elapsedMs = (System.currentTimeMillis() - startHopTime).toDouble()
                    val parsed = TracerouteOutputParser.parseHopOutput(ttl, outputLines, targetIp, elapsedMs)

                    if (!parsed.isTimeout && parsed.ip != null) {
                        consecutiveTimeouts = 0

                        var hostname: String? = null
                        try {
                            val hInet = InetAddress.getByName(parsed.ip)
                            val hostNameStr = hInet.canonicalHostName
                            if (hostNameStr != parsed.ip) {
                                hostname = hostNameStr
                            }
                        } catch (_: Exception) {}

                        val hop = TracerouteHop(
                            hop = ttl,
                            ip = parsed.ip,
                            hostname = hostname,
                            rttMs = parsed.rttMs,
                            isTimeout = false,
                            isDestination = parsed.isDestinationReached
                        )
                        hopsList.add(hop)
                        _tracerouteState.value = _tracerouteState.value.copy(hops = hopsList.toList())

                        if (enableGeoIp) {
                            scope.launch {
                                val geo = geoIpService.lookup(parsed.ip)
                                val updatedHops = _tracerouteState.value.hops.map { h ->
                                    if (h.hop == ttl && h.ip == parsed.ip) {
                                        h.copy(geoCountry = geo.country, geoCountryCode = geo.countryCode)
                                    } else h
                                }
                                _tracerouteState.value = _tracerouteState.value.copy(hops = updatedHops)
                            }
                        }

                        if (parsed.isDestinationReached) {
                            break
                        }
                    } else {
                        // Timeout
                        consecutiveTimeouts++
                        val timeoutHop = TracerouteHop(
                            hop = ttl,
                            ip = "* * *",
                            hostname = null,
                            rttMs = null,
                            isTimeout = true,
                            isDestination = false
                        )
                        hopsList.add(timeoutHop)
                        _tracerouteState.value = _tracerouteState.value.copy(hops = hopsList.toList())

                        // Dừng nếu không nhận được phản hồi quá 6 hop liên tiếp
                        if (consecutiveTimeouts >= 6) {
                            break
                        }
                    }
                }

                _tracerouteState.value = _tracerouteState.value.copy(
                    isRunning = false,
                    hops = hopsList.toList()
                )

            } catch (e: CancellationException) {
                _tracerouteState.value = _tracerouteState.value.copy(isRunning = false)
            } catch (e: Exception) {
                _tracerouteState.value = _tracerouteState.value.copy(
                    isRunning = false,
                    errorMessage = "Lỗi khi traceroute: ${e.localizedMessage}"
                )
            }
        }
    }

    fun stopTraceroute() {
        traceJob?.cancel()
        traceJob = null
        _tracerouteState.value = _tracerouteState.value.copy(isRunning = false)
    }
}
