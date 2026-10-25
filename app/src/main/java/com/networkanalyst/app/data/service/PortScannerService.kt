package com.networkanalyst.app.data.service

import com.networkanalyst.app.R
import com.networkanalyst.app.data.model.PortScanItem
import com.networkanalyst.app.data.model.PortState
import com.networkanalyst.app.ui.components.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

data class PortScanProgress(
    val isScanning: Boolean = false,
    val isPaused: Boolean = false,
    val target: String = "",
    val resolvedIp: String? = null,
    val isLanTarget: Boolean = false,
    val timeoutUsedMs: Int = 200,
    val concurrencyUsed: Int = 200,
    val scannedCount: Int = 0,
    val totalCount: Int = 0,
    val currentPort: Int? = null,
    val openPorts: List<PortScanItem> = emptyList(),
    val closedCount: Int = 0,
    val filteredCount: Int = 0,
    val errorCount: Int = 0,
    val error: UiText? = null
)

@Singleton
class PortScannerService @Inject constructor() {
    private val _scanState = MutableStateFlow(PortScanProgress())
    val scanState: StateFlow<PortScanProgress> = _scanState.asStateFlow()

    private var scanJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    // Lưu danh sách cổng chưa quét để phục vụ tính năng Resume (Tiếp tục)
    private var pendingPorts: List<Int> = emptyList()
    private var cachedResolvedIp: String? = null
    private var cachedTargetHost: String = ""
    private var cachedEffectiveTimeout: Int = 200
    private var cachedConcurrency: Int = 200
    private var cachedBannerGrab: Boolean = false

    companion object {
        val COMMON_PORTS = listOf(
            21, 22, 23, 25, 53, 80, 110, 111, 135, 139, 143, 443, 445, 993, 995,
            1433, 1521, 3306, 3389, 5432, 5900, 6379, 8000, 8080, 8443, 8888, 9000, 27017
        )

        const val FULL_PORT_COUNT = 65535
        const val MAX_CONCURRENCY_CEILING = 500

        private val SERVICE_MAP = mapOf(
            21 to "FTP (File Transfer Protocol)",
            22 to "SSH (Secure Shell)",
            23 to "Telnet",
            25 to "SMTP (Simple Mail Transfer)",
            53 to "DNS (Domain Name System)",
            80 to "HTTP (Web Server)",
            110 to "POP3 (Post Office Protocol 3)",
            111 to "RPCbind",
            135 to "MSRPC (Microsoft RPC)",
            139 to "NetBIOS Session Service",
            143 to "IMAP (Internet Message Access)",
            443 to "HTTPS (HTTP over TLS/SSL)",
            445 to "SMB / Microsoft-DS",
            993 to "IMAPS (IMAP over SSL)",
            995 to "POP3S (POP3 over SSL)",
            1433 to "MS-SQL (Microsoft SQL Server)",
            1521 to "Oracle Database",
            3306 to "MySQL Database",
            3389 to "RDP (Remote Desktop Protocol)",
            5432 to "PostgreSQL Database",
            5900 to "VNC (Virtual Network Computing)",
            6379 to "Redis Database",
            8000 to "HTTP Alternate / Dev Server",
            8080 to "HTTP Proxy / Tomcat / Alt Web",
            8443 to "HTTPS Alternate",
            8888 to "HTTP Alternate / Jupyter",
            9000 to "SonarQube / PHP-FPM",
            27017 to "MongoDB Database"
        )
    }

    fun startScan(
        host: String,
        ports: List<Int>,
        concurrencyLimit: Int = 200,
        customTimeoutMs: Int? = null,
        enableBannerGrab: Boolean = false
    ) {
        stopScan()

        scanJob = scope.launch {
            val cleanHost = host.trim()
            if (cleanHost.isEmpty()) {
                _scanState.value = PortScanProgress(
                    error = UiText.StringResource(R.string.err_invalid_target)
                )
                return@launch
            }

            if (ports.isEmpty()) {
                _scanState.value = PortScanProgress(
                    error = UiText.StringResource(R.string.err_port_list_empty)
                )
                return@launch
            }

            try {
                // Pre-resolve IP to classify LAN/WAN
                val inetAddress = withContext(Dispatchers.IO) {
                    try {
                        InetAddress.getByName(cleanHost)
                    } catch (e: Exception) {
                        null
                    }
                }

                if (inetAddress == null) {
                    _scanState.value = PortScanProgress(
                        isScanning = false,
                        target = cleanHost,
                        error = UiText.StringResource(R.string.err_host_unresolved, cleanHost)
                    )
                    return@launch
                }

                val ip = inetAddress.hostAddress ?: cleanHost
                val isLan = PortRangeParser.isPrivateIp(ip)

                // Timeout thích ứng: ngắn cho LAN (~200ms), dài hơn cho WAN (~500ms)
                val effectiveTimeout = customTimeoutMs ?: if (isLan) 200 else 500
                val safeConcurrency = concurrencyLimit.coerceIn(1, MAX_CONCURRENCY_CEILING)

                cachedResolvedIp = ip
                cachedTargetHost = cleanHost
                cachedEffectiveTimeout = effectiveTimeout
                cachedConcurrency = safeConcurrency
                cachedBannerGrab = enableBannerGrab
                pendingPorts = ports

                _scanState.value = PortScanProgress(
                    isScanning = true,
                    isPaused = false,
                    target = cleanHost,
                    resolvedIp = ip,
                    isLanTarget = isLan,
                    timeoutUsedMs = effectiveTimeout,
                    concurrencyUsed = safeConcurrency,
                    totalCount = ports.size
                )

                executePortScanning(
                    ip = ip,
                    portsToScan = ports,
                    concurrency = safeConcurrency,
                    timeoutMs = effectiveTimeout,
                    enableBannerGrab = enableBannerGrab
                )

            } catch (e: CancellationException) {
                _scanState.value = _scanState.value.copy(isScanning = false)
            } catch (e: Exception) {
                _scanState.value = _scanState.value.copy(
                    isScanning = false,
                    error = UiText.StringResource(R.string.err_generic_service_error, "Port scan", e.localizedMessage ?: "")
                )
            }
        }
    }

    fun pauseScan() {
        scanJob?.cancel()
        scanJob = null
        _scanState.value = _scanState.value.copy(
            isScanning = false,
            isPaused = true
        )
    }

    fun resumeScan(enableBannerGrab: Boolean? = null) {
        if (!_scanState.value.isPaused || pendingPorts.isEmpty()) return

        val ip = cachedResolvedIp ?: return
        val bannerGrab = enableBannerGrab ?: cachedBannerGrab

        scanJob = scope.launch {
            _scanState.value = _scanState.value.copy(
                isScanning = true,
                isPaused = false
            )

            executePortScanning(
                ip = ip,
                portsToScan = pendingPorts,
                concurrency = cachedConcurrency,
                timeoutMs = cachedEffectiveTimeout,
                enableBannerGrab = bannerGrab
            )
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        pendingPorts = emptyList()
        _scanState.value = _scanState.value.copy(
            isScanning = false,
            isPaused = false
        )
    }

    private suspend fun executePortScanning(
        ip: String,
        portsToScan: List<Int>,
        concurrency: Int,
        timeoutMs: Int,
        enableBannerGrab: Boolean
    ) {
        val semaphore = Semaphore(concurrency)
        val openList = _scanState.value.openPorts.toMutableList()
        var closedCount = _scanState.value.closedCount
        var filteredCount = _scanState.value.filteredCount
        var errorCount = _scanState.value.errorCount
        var scanned = _scanState.value.scannedCount

        val remainingList = portsToScan.toMutableList()

        try {
            val tasks = portsToScan.map { port ->
                scope.async(Dispatchers.IO) {
                    if (!isActive) return@async
                    val result = semaphore.withPermit {
                        probePort(ip, port, timeoutMs, enableBannerGrab)
                    }

                    scanned++
                    synchronized(openList) {
                        remainingList.remove(port)
                        pendingPorts = remainingList.toList()

                        when (result.state) {
                            PortState.OPEN -> {
                                openList.add(result)
                                openList.sortBy { it.port }
                            }
                            PortState.CLOSED -> closedCount++
                            PortState.FILTERED -> filteredCount++
                            PortState.ERROR -> errorCount++
                        }

                        _scanState.value = _scanState.value.copy(
                            scannedCount = scanned,
                            currentPort = port,
                            openPorts = openList.toList(),
                            closedCount = closedCount,
                            filteredCount = filteredCount,
                            errorCount = errorCount
                        )
                    }
                }
            }

            tasks.awaitAll()

            _scanState.value = _scanState.value.copy(
                isScanning = false,
                isPaused = false,
                currentPort = null,
                openPorts = openList.toList(),
                closedCount = closedCount,
                filteredCount = filteredCount,
                errorCount = errorCount
            )
            pendingPorts = emptyList()

        } catch (e: CancellationException) {
            // Khi hủy hoặc tạm dừng, giữ nguyên openPorts, counts và pendingPorts
            _scanState.value = _scanState.value.copy(isScanning = false)
            throw e
        }
    }

    /**
     * Dò cổng với cơ chế đóng socket giải phóng File Descriptor ngay khi coroutine bị hủy.
     * Tránh treo blocking connect() và triệt tiêu nguy cơ rò rỉ FD.
     */
    private suspend fun probePort(
        ip: String,
        port: Int,
        timeoutMs: Int,
        enableBannerGrab: Boolean
    ): PortScanItem = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            var socket: Socket? = null
            val startTime = System.currentTimeMillis()

            continuation.invokeOnCancellation {
                try {
                    socket?.close()
                } catch (_: Throwable) {}
            }

            try {
                socket = Socket()
                socket.soTimeout = timeoutMs
                socket.connect(InetSocketAddress(ip, port), timeoutMs)

                val elapsed = System.currentTimeMillis() - startTime
                val serviceName = SERVICE_MAP[port] ?: "Port $port"

                val banner = if (enableBannerGrab) grabBanner(socket, port) else null

                continuation.resume(
                    PortScanItem(
                        port = port,
                        state = PortState.OPEN,
                        serviceName = serviceName,
                        banner = banner,
                        responseTimeMs = elapsed
                    )
                )
            } catch (e: ConnectException) {
                // RST received -> Closed
                continuation.resume(
                    PortScanItem(
                        port = port,
                        state = PortState.CLOSED,
                        serviceName = SERVICE_MAP[port] ?: "Port $port",
                        responseTimeMs = System.currentTimeMillis() - startTime
                    )
                )
            } catch (e: SocketTimeoutException) {
                // Timeout -> Filtered
                continuation.resume(
                    PortScanItem(
                        port = port,
                        state = PortState.FILTERED,
                        serviceName = SERVICE_MAP[port] ?: "Port $port",
                        responseTimeMs = timeoutMs.toLong()
                    )
                )
            } catch (e: CancellationException) {
                // Cancelled
            } catch (e: Exception) {
                if (continuation.isActive) {
                    continuation.resume(
                        PortScanItem(
                            port = port,
                            state = PortState.ERROR,
                            serviceName = SERVICE_MAP[port] ?: "Port $port",
                            banner = e.localizedMessage
                        )
                    )
                }
            } finally {
                try {
                    socket?.close()
                } catch (_: Throwable) {}
            }
        }
    }

    /**
     * Banner grabbing an toàn: giới hạn timeout (500ms) và số byte đọc (tối đa 1024 bytes).
     */
    private fun grabBanner(socket: Socket, port: Int): String? {
        return try {
            socket.soTimeout = 500
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()), 1024)

            when (port) {
                21, 25, 110 -> {
                    reader.readLine()?.take(256)?.trim()
                }
                22 -> {
                    reader.readLine()?.take(256)?.trim()
                }
                80, 8080, 8000 -> {
                    val writer = OutputStreamWriter(socket.getOutputStream())
                    writer.write("HEAD / HTTP/1.0\r\nHost: localhost\r\n\r\n")
                    writer.flush()
                    var line: String? = null
                    var serverHeader: String? = null
                    var linesRead = 0
                    while (linesRead < 20 && reader.readLine().also { line = it } != null) {
                        linesRead++
                        val l = line ?: break
                        if (l.startsWith("Server:", ignoreCase = true)) {
                            serverHeader = l.substringAfter(":").take(128).trim()
                            break
                        }
                    }
                    serverHeader
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}
