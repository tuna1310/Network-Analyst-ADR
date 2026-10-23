package com.networkanalyst.app.data.service

import com.networkanalyst.app.data.model.PingPacket
import com.networkanalyst.app.data.model.PingSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

data class PingSessionState(
    val isRunning: Boolean = false,
    val target: String = "",
    val resolvedIp: String? = null,
    val packets: List<PingPacket> = emptyList(),
    val summary: PingSummary? = null,
    val errorMessage: String? = null
)

@Singleton
class PingService @Inject constructor() {
    private val _pingState = MutableStateFlow(PingSessionState())
    val pingState: StateFlow<PingSessionState> = _pingState.asStateFlow()

    private var pingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun startPing(host: String, count: Int = 4, timeoutSec: Int = 2) {
        stopPing()

        pingJob = scope.launch {
            val cleanHost = host.trim()
            if (cleanHost.isEmpty()) {
                _pingState.value = PingSessionState(
                    errorMessage = "Vui lòng nhập địa chỉ IP hoặc tên miền hợp lệ."
                )
                return@launch
            }

            _pingState.value = PingSessionState(
                isRunning = true,
                target = cleanHost
            )

            try {
                // 1. Resolve host
                val inetAddress = withContext(Dispatchers.IO) {
                    try {
                        InetAddress.getByName(cleanHost)
                    } catch (e: Exception) {
                        null
                    }
                }

                if (inetAddress == null) {
                    _pingState.value = PingSessionState(
                        isRunning = false,
                        target = cleanHost,
                        errorMessage = "Không thể phân giải tên miền: $cleanHost"
                    )
                    return@launch
                }

                val ip = inetAddress.hostAddress ?: cleanHost
                _pingState.value = _pingState.value.copy(resolvedIp = ip)

                // 2. Try System Ping with ProcessBuilder first
                val systemPingWorked = runSystemPing(cleanHost, ip, count, timeoutSec)

                if (!systemPingWorked && isActive) {
                    // Fallback to InetAddress.isReachable()
                    runIsReachableFallback(cleanHost, inetAddress, count, timeoutSec * 1000)
                }

            } catch (e: CancellationException) {
                _pingState.value = _pingState.value.copy(isRunning = false)
            } catch (e: Exception) {
                _pingState.value = _pingState.value.copy(
                    isRunning = false,
                    errorMessage = "Lỗi khi ping: ${e.localizedMessage}"
                )
            }
        }
    }

    fun stopPing() {
        pingJob?.cancel()
        pingJob = null
        _pingState.value = _pingState.value.copy(isRunning = false)
    }

    private suspend fun runSystemPing(
        host: String,
        ip: String,
        count: Int,
        timeoutSec: Int
    ): Boolean = withContext(Dispatchers.IO) {
        val packetsList = mutableListOf<PingPacket>()
        var process: Process? = null
        val maxWaitMs = (count * timeoutSec * 1500L).coerceAtLeast(4000L)

        try {
            val pingExecutable = when {
                java.io.File("/system/bin/ping").exists() -> "/system/bin/ping"
                else -> "ping"
            }

            val processBuilder = ProcessBuilder(
                pingExecutable,
                "-c", count.toString(),
                "-W", timeoutSec.toString(),
                host
            )
            processBuilder.redirectErrorStream(true)
            process = processBuilder.start()

            var transmitted = count
            var received = 0
            var lossPercent = 0.0
            var minRtt = 0.0
            var avgRtt = 0.0
            var maxRtt = 0.0
            var mdevRtt = 0.0
            var parsedAnyPacket = false
            val errorOutput = StringBuilder()

            val success = withTimeoutOrNull(maxWaitMs) {
                val reader = BufferedReader(InputStreamReader(process?.inputStream))
                var line: String? = null

                while (isActive && reader.readLine().also { line = it } != null) {
                    val currentLine = line ?: continue
                    errorOutput.appendLine(currentLine)

                    val packet = PingOutputParser.parsePacketLine(currentLine, ip, packetsList.size + 1)
                    if (packet != null) {
                        parsedAnyPacket = true
                        packetsList.add(packet)
                        _pingState.value = _pingState.value.copy(
                            packets = packetsList.toList()
                        )
                    }

                    val summary = PingOutputParser.parseSummaryLine(currentLine)
                    if (summary != null) {
                        transmitted = summary.transmitted
                        received = summary.received
                        lossPercent = summary.lossPercent
                    }

                    val rtt = PingOutputParser.parseRttLine(currentLine)
                    if (rtt != null) {
                        minRtt = rtt.minMs
                        avgRtt = rtt.avgMs
                        maxRtt = rtt.maxMs
                        mdevRtt = rtt.mdevMs
                    }
                }
                process?.waitFor(1, TimeUnit.SECONDS)
                true
            }

            if (success == null) {
                // Timeout quá thời hạn cho phép
                process?.destroyForcibly()
                if (packetsList.isEmpty()) {
                    _pingState.value = _pingState.value.copy(
                        errorMessage = "Hết thời gian chờ phản hồi từ lệnh ping hệ thống."
                    )
                }
            }

            if (parsedAnyPacket || packetsList.isNotEmpty()) {
                if (received == 0) received = packetsList.count { it.isSuccess }
                if (avgRtt == 0.0 && packetsList.isNotEmpty()) {
                    val rtts = packetsList.map { it.rttMs }
                    minRtt = rtts.minOrNull() ?: 0.0
                    maxRtt = rtts.maxOrNull() ?: 0.0
                    avgRtt = rtts.average()
                }

                val summary = PingSummary(
                    target = host,
                    ip = ip,
                    transmitted = transmitted,
                    received = received,
                    lossPercentage = lossPercent,
                    minRttMs = minRtt,
                    avgRttMs = avgRtt,
                    maxRttMs = maxRtt,
                    mdevRttMs = mdevRtt
                )

                _pingState.value = _pingState.value.copy(
                    isRunning = false,
                    packets = packetsList.toList(),
                    summary = summary
                )
                true
            } else {
                // Không bắt được gói tin nào từ binary ping của hãng
                val errorMsg = PingOutputParser.parseErrorMessage(errorOutput.toString())
                if (errorMsg.isNotBlank() && !errorMsg.contains("chuyển sang")) {
                    _pingState.value = _pingState.value.copy(
                        errorMessage = errorMsg
                    )
                }
                false
            }
        } catch (e: Exception) {
            false
        } finally {
            try {
                process?.destroyForcibly()
            } catch (_: Exception) {}
        }
    }

    /**
     * Cơ chế dự phòng sử dụng InetAddress.isReachable:
     * LƯU Ý KỸ THUẬT:
     * Trên Android không root (non-root), JVM không được cấp quyền mở raw ICMP socket.
     * Do đó, isReachable() thường cố gắng gửi ICMP echo request, nhưng nếu không có quyền
     * nó sẽ âm thầm rơi về (fallback) thử kết nối TCP port 7 (Echo service).
     * Vì đa số máy chủ hiện đại đều chặn TCP port 7, isReachable() có thể trả về false
     * ngay cả khi máy chủ vẫn đang online, hoặc giá trị RTT đo được không phản ánh đúng ICMP RTT.
     * Ứng dụng chỉ dùng phương thức này làm giải pháp dự phòng cuối cùng.
     */
    private suspend fun runIsReachableFallback(
        host: String,
        inetAddress: InetAddress,
        count: Int,
        timeoutMs: Int
    ) = withContext(Dispatchers.IO) {
        val packetsList = mutableListOf<PingPacket>()
        val ip = inetAddress.hostAddress ?: host
        val rtts = mutableListOf<Double>()
        var successCount = 0

        for (i in 1..count) {
            if (!isActive) break

            val startTime = System.nanoTime()
            val reachable = try {
                inetAddress.isReachable(timeoutMs)
            } catch (e: Exception) {
                false
            }
            val elapsedMs = (System.nanoTime() - startTime) / 1_000_000.0

            if (reachable) {
                successCount++
                rtts.add(elapsedMs)
                packetsList.add(
                    PingPacket(
                        sequence = i,
                        bytes = 64,
                        ip = ip,
                        rttMs = elapsedMs,
                        ttl = 64,
                        isSuccess = true
                    )
                )
            } else {
                packetsList.add(
                    PingPacket(
                        sequence = i,
                        bytes = 0,
                        ip = ip,
                        rttMs = 0.0,
                        ttl = 0,
                        isSuccess = false,
                        errorMessage = "Hết thời gian chờ (Request timed out)"
                    )
                )
            }

            _pingState.value = _pingState.value.copy(
                packets = packetsList.toList()
            )

            if (i < count) {
                delay(1000)
            }
        }

        val loss = ((count - successCount).toDouble() / count) * 100.0
        val min = rtts.minOrNull() ?: 0.0
        val max = rtts.maxOrNull() ?: 0.0
        val avg = if (rtts.isNotEmpty()) rtts.average() else 0.0

        val summary = PingSummary(
            target = host,
            ip = ip,
            transmitted = count,
            received = successCount,
            lossPercentage = loss,
            minRttMs = min,
            avgRttMs = avg,
            maxRttMs = max,
            mdevRttMs = 0.0
        )

        _pingState.value = _pingState.value.copy(
            isRunning = false,
            summary = summary
        )
    }
}
