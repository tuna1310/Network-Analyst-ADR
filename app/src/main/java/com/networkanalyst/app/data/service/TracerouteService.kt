package com.networkanalyst.app.data.service

import com.networkanalyst.app.R
import com.networkanalyst.app.data.model.TracerouteHop
import com.networkanalyst.app.ui.components.UiText
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
    val error: UiText? = null
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
                    error = UiText.StringResource(R.string.err_invalid_target)
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
                        error = UiText.StringResource(R.string.err_host_unresolved, cleanHost)
                    )
                    return@launch
                }

                val targetIp = targetInet.hostAddress ?: cleanHost
                _tracerouteState.value = _tracerouteState.value.copy(resolvedIp = targetIp)

                val hopsList = mutableListOf<TracerouteHop>()
                var consecutiveTimeouts = 0

                val isIpv6 = targetIp.contains(":")
                val pingExecutable = when {
                    isIpv6 && java.io.File("/system/bin/ping6").exists() -> "/system/bin/ping6"
                    java.io.File("/system/bin/ping").exists() -> "/system/bin/ping"
                    isIpv6 -> "ping6"
                    else -> "ping"
                }

                val baseArgs = mutableListOf(pingExecutable)
                if (isIpv6 && pingExecutable != "/system/bin/ping6") {
                    baseArgs.add("-6")
                }

                for (ttl in 1..maxHops) {
                    if (!isActive) break

                    _tracerouteState.value = _tracerouteState.value.copy(currentHop = ttl)

                    val startHopTime = System.currentTimeMillis()
                    val outputLines = mutableListOf<String>()

                    withTimeoutOrNull(2500L) {
                        var process: Process? = null
                        try {
                            val cmd = ArrayList(baseArgs).apply {
                                addAll(listOf("-c", "1", "-W", "2", "-t", ttl.toString(), cleanHost))
                            }
                            val pb = ProcessBuilder(cmd)
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
                            error = UiText.StringResource(R.string.err_traceroute_ttl_unsupported)
                        )
                        return@launch
                    }

                    val elapsedMs = (System.currentTimeMillis() - startHopTime).toDouble()
                    val parsed = TracerouteOutputParser.parseHopOutput(ttl, outputLines, targetIp, elapsedMs)

                    if (!parsed.isTimeout && parsed.ip != null) {
                        consecutiveTimeouts = 0

                        var hostname: String? = null
                        try {
                            val hostNameStr = withTimeoutOrNull(1500L) {
                                withContext(Dispatchers.IO) {
                                    val hInet = InetAddress.getByName(parsed.ip)
                                    hInet.canonicalHostName
                                }
                            }
                            if (hostNameStr != null && hostNameStr != parsed.ip && hostNameStr.isNotBlank()) {
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
                    error = UiText.StringResource(R.string.err_generic_service_error, "Traceroute", e.localizedMessage ?: "")
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
