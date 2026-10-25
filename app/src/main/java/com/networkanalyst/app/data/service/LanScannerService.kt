package com.networkanalyst.app.data.service

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.networkanalyst.app.R
import com.networkanalyst.app.data.model.DiscoveryProtocol
import com.networkanalyst.app.data.model.LanDevice
import com.networkanalyst.app.ui.components.UiText
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

data class LanScanProgress(
    val isScanning: Boolean = false,
    val scannedCount: Int = 0,
    val totalCount: Int = 0,
    val currentIp: String? = null,
    val devices: List<LanDevice> = emptyList(),
    val error: UiText? = null,
    val warning: UiText? = null
)

@Singleton
class LanScannerService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val diagnosticsService: DiagnosticsHelper
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val _scanProgress = MutableStateFlow(LanScanProgress())
    val scanProgress: StateFlow<LanScanProgress> = _scanProgress.asStateFlow()

    private var scanJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var multicastLock: WifiManager.MulticastLock? = null

    private val discoveredMap = ConcurrentHashMap<String, LanDevice>()

    fun startScan() {
        stopScan()
        discoveredMap.clear()

        scanJob = scope.launch {
            _scanProgress.value = LanScanProgress(isScanning = true)

            try {
                acquireMulticastLock()

                val netInfo = diagnosticsService.getNetworkInfoSnapshot()
                val localIp = netInfo.localIpv4
                val prefix = netInfo.prefixLength ?: 24
                val gatewayIp = netInfo.gateway

                if (localIp.isNullOrBlank()) {
                    _scanProgress.value = LanScanProgress(
                        isScanning = false,
                        error = UiText.StringResource(R.string.err_lan_no_local_ip)
                    )
                    return@launch
                }

                // Add current device (Lỗi 1.5: String resource)
                val currentDevice = LanDevice(
                    ip = localIp,
                    hostname = context.getString(R.string.lanscan_this_device),
                    vendor = context.getString(R.string.lanscan_android_device),
                    protocols = setOf(DiscoveryProtocol.TCP_PROBE),
                    isCurrentDevice = true
                )
                discoveredMap[localIp] = currentDevice

                val isPartial = isPartialSubnet(prefix)
                val partialWarning = if (isPartial) UiText.StringResource(R.string.lanscan_partial_subnet_warning) else null
                updateProgress(0, 0, localIp, partialWarning)

                // 1. Start mDNS discovery in background
                startMdnsDiscovery()

                // 2. Generate Subnet IP List
                val ipsToScan = generateSubnetIps(localIp, prefix).filter { it != localIp }
                val total = ipsToScan.size
                val completed = AtomicInteger(0) // Lỗi 1.2: AtomicInteger an toàn luồng

                val socketSemaphore = Semaphore(40) // Giới hạn tổng số socket đồng thời

                val scanTasks = ipsToScan.map { targetIp ->
                    async(Dispatchers.IO) {
                        if (!isActive) return@async
                        probeDevice(
                            ip = targetIp,
                            isGateway = targetIp == gatewayIp,
                            socketSemaphore = socketSemaphore
                        )
                        val currCompleted = completed.incrementAndGet()
                        updateProgress(currCompleted, total, targetIp, partialWarning)
                    }
                }

                scanTasks.awaitAll()

                // Final refresh
                _scanProgress.value = _scanProgress.value.copy(
                    isScanning = false,
                    scannedCount = total,
                    totalCount = total,
                    currentIp = null,
                    devices = discoveredMap.values.sortedBy { ipToLong(it.ip) },
                    warning = partialWarning
                )
            } catch (e: CancellationException) {
                // Cancelled normally
            } catch (e: Exception) {
                _scanProgress.value = _scanProgress.value.copy(
                    isScanning = false,
                    error = UiText.StringResource(R.string.err_generic_service_error, "LAN scan", e.localizedMessage ?: "")
                )
            } finally {
                releaseMulticastLock()
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        releaseMulticastLock()
        _scanProgress.value = _scanProgress.value.copy(isScanning = false)
    }

    private fun updateProgress(scanned: Int, total: Int, currentIp: String?, warning: UiText? = null) {
        _scanProgress.value = _scanProgress.value.copy(
            isScanning = true,
            scannedCount = scanned,
            totalCount = total,
            currentIp = currentIp,
            devices = discoveredMap.values.sortedBy { ipToLong(it.ip) },
            warning = warning
        )
    }

    private suspend fun probeDevice(ip: String, isGateway: Boolean, socketSemaphore: Semaphore) {
        val protocols = mutableSetOf<DiscoveryProtocol>()
        val openPorts = mutableListOf<Int>()
        var responseTime: Long? = null
        var hostname: String? = null
        var mac: String? = null
        var vendor: String? = null

        if (isGateway) {
            protocols.add(DiscoveryProtocol.GATEWAY)
        }

        // 1. Parallel Quick TCP Port Probe & Ping (Lỗi 1.3: Thăm dò song song, dừng sớm khi có cổng mở, giới hạn semaphore socket)
        val commonPorts = intArrayOf(80, 443, 22, 445, 8080, 53, 8008, 9100)
        val startTime = System.currentTimeMillis()
        var reachable = false

        coroutineScope {
            val openPortDeferred = CompletableDeferred<Int>()
            val activeCount = AtomicInteger(commonPorts.size)

            val portJobs = commonPorts.map { port ->
                launch(Dispatchers.IO) {
                    try {
                        socketSemaphore.withPermit {
                            if (openPortDeferred.isCompleted || !isActive) return@withPermit
                            Socket().use { socket ->
                                socket.connect(InetSocketAddress(ip, port), 200)
                                openPortDeferred.complete(port)
                            }
                        }
                    } catch (_: Exception) {
                    } finally {
                        if (activeCount.decrementAndGet() == 0) {
                            openPortDeferred.complete(-1)
                        }
                    }
                }
            }

            val firstOpen = try {
                openPortDeferred.await()
            } catch (_: Exception) {
                -1
            }

            if (firstOpen > 0) {
                portJobs.forEach { it.cancel() }
                protocols.add(DiscoveryProtocol.TCP_PROBE)
                openPorts.add(firstOpen)
                reachable = true
                responseTime = System.currentTimeMillis() - startTime
            }
        }

        // Ping check fallback
        if (!reachable) {
            try {
                val inet = InetAddress.getByName(ip)
                if (inet.isReachable(200)) {
                    reachable = true
                    protocols.add(DiscoveryProtocol.TCP_PROBE)
                    responseTime = System.currentTimeMillis() - startTime
                }
            } catch (_: Exception) {}
        }

        // 2. NetBIOS Query (UDP port 137) - Gets Windows/Samba name and MAC address!
        val netbiosResult = queryNetBios(ip)
        val netbiosHostname = netbiosResult?.hostname
        if (netbiosResult != null) {
            reachable = true
            protocols.add(DiscoveryProtocol.NETBIOS)
            if (mac == null && netbiosResult.mac != null) {
                mac = netbiosResult.mac
            }
        }

        // 3. LLMNR Query (UDP port 5355)
        val llmnrHostname = queryLlmnr(ip)
        if (llmnrHostname != null) {
            reachable = true
            protocols.add(DiscoveryProtocol.LLMNR)
        }

        // 4. SNMP Query (UDP port 161)
        val snmpSysDescr = querySnmp(ip)
        if (snmpSysDescr != null) {
            reachable = true
            protocols.add(DiscoveryProtocol.SNMP)
            if (vendor == null) {
                vendor = parseSnmpVendor(snmpSysDescr)
            }
        }

        // 5. Reverse DNS / PTR Query (Lỗi 1.4: bọc timeout 1500ms)
        var ptrHostname: String? = null
        if (reachable) {
            try {
                val canonical = withTimeoutOrNull(1500L) {
                    withContext(Dispatchers.IO) {
                        InetAddress.getByName(ip).canonicalHostName
                    }
                }
                if (canonical != null && canonical != ip && canonical.isNotBlank()) {
                    ptrHostname = canonical
                    protocols.add(DiscoveryProtocol.REVERSE_DNS)
                }
            } catch (_: Exception) {}

            val existing = discoveredMap[ip]
            val mdnsCandidate = existing?.hostname?.takeIf { existing.protocols.contains(DiscoveryProtocol.MDNS) }

            // Áp dụng thứ tự ưu tiên chuẩn: mDNS > NetBIOS > LLMNR > PTR
            hostname = HostnameResolutionHelper.selectBestHostname(
                ip = ip,
                mdns = mdnsCandidate,
                netbios = netbiosHostname,
                llmnr = llmnrHostname,
                ptr = ptrHostname
            )

            if (vendor == null) {
                vendor = guessVendor(mac, hostname, openPorts, isGateway)
            }

            val mergedProtocols = (existing?.protocols ?: emptySet()) + protocols
            val mergedPorts = ((existing?.openPorts ?: emptyList()) + openPorts).distinct()

            val device = LanDevice(
                ip = ip,
                ipv6 = existing?.ipv6,
                ipv6Reason = existing?.ipv6Reason ?: "No AAAA record via mDNS",
                mac = mac ?: existing?.mac,
                hostname = hostname ?: existing?.hostname ?: if (isGateway) "Default Gateway / Router" else null,
                vendor = vendor ?: existing?.vendor,
                responseTimeMs = responseTime ?: existing?.responseTimeMs,
                protocols = mergedProtocols,
                openPorts = mergedPorts,
                isGateway = isGateway
            )

            discoveredMap[ip] = device
        }
    }

    @Suppress("DEPRECATION")
    private fun startMdnsDiscovery() {
        val nsd = nsdManager ?: return
        val serviceTypes = listOf("_http._tcp.", "_workstation._tcp.", "_googlecast._tcp.", "_ipp._tcp.", "_airplay._tcp.")

        for (type in serviceTypes) {
            try {
                val listener = object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(regType: String) {}
                    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                        try {
                            nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                                override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                                    val host = resolvedInfo.host?.hostAddress ?: return
                                    val name = resolvedInfo.serviceName
                                    val port = resolvedInfo.port
                                    val existing = discoveredMap[host]

                                    // Trích xuất IPv6 từ mDNS phản hồi nếu có
                                    var v6Address: String? = existing?.ipv6
                                    if (resolvedInfo.host is java.net.Inet6Address) {
                                        v6Address = resolvedInfo.host.hostAddress
                                    }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                        val v6 = resolvedInfo.hostAddresses.firstOrNull { it is java.net.Inet6Address }?.hostAddress
                                        if (v6 != null) {
                                            v6Address = v6
                                        }
                                    }

                                    val protocols = (existing?.protocols ?: emptySet()) + DiscoveryProtocol.MDNS
                                    val ports = if (port > 0) ((existing?.openPorts ?: emptyList()) + port).distinct() else existing?.openPorts ?: emptyList()

                                    val updatedHostname = HostnameResolutionHelper.selectBestHostname(
                                        ip = host,
                                        mdns = name,
                                        netbios = null,
                                        llmnr = null,
                                        ptr = existing?.hostname
                                    ) ?: existing?.hostname ?: name

                                    val updated = LanDevice(
                                        ip = host,
                                        ipv6 = v6Address,
                                        ipv6Reason = if (v6Address == null) "No AAAA record via mDNS" else null,
                                        mac = existing?.mac,
                                        hostname = updatedHostname,
                                        vendor = existing?.vendor ?: guessVendorFromMdns(type, name),
                                        responseTimeMs = existing?.responseTimeMs,
                                        protocols = protocols,
                                        openPorts = ports,
                                        isGateway = existing?.isGateway ?: false
                                    )
                                    discoveredMap[host] = updated
                                    _scanProgress.value = _scanProgress.value.copy(
                                        devices = discoveredMap.values.sortedBy { ipToLong(it.ip) }
                                    )
                                }
                            })
                        } catch (_: Exception) {}
                    }
                    override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
                    override fun onDiscoveryStopped(serviceType: String) {}
                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                }
                nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
            } catch (_: Exception) {}
        }
    }

    private data class NetBiosResult(val hostname: String?, val mac: String?)

    private fun queryNetBios(ip: String): NetBiosResult? {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket()
            socket.soTimeout = 250

            // RFC 1002 NBSTAT wildcard query
            val query = byteArrayOf(
                0x13.toByte(), 0x37.toByte(), // Transaction ID
                0x00, 0x00, // Flags
                0x00, 0x01, // Questions = 1
                0x00, 0x00, // Answer RRs
                0x00, 0x00, // Authority RRs
                0x00, 0x00, // Additional RRs
                0x20, // Name length (32 bytes)
                0x43, 0x4B, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41,
                0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41,
                0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41,
                0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41,
                0x00, // Terminator
                0x00, 0x21, // Type = NBSTAT (33)
                0x00, 0x01  // Class = IN
            )

            val address = InetAddress.getByName(ip)
            val packet = DatagramPacket(query, query.size, address, 137)
            socket.send(packet)

            val buffer = ByteArray(1024)
            val recvPacket = DatagramPacket(buffer, buffer.size)
            socket.receive(recvPacket)

            val len = recvPacket.length
            if (len > 57) {
                val numNames = buffer[56].toInt() and 0xFF
                var computerName: String? = null
                var macStr: String? = null

                var offset = 57
                for (i in 0 until numNames) {
                    if (offset + 18 <= len) {
                        val nameBytes = ByteArray(15)
                        System.arraycopy(buffer, offset, nameBytes, 0, 15)
                        val type = buffer[offset + 15].toInt() and 0xFF
                        if (computerName == null && (type == 0x00 || type == 0x20)) {
                            computerName = String(nameBytes).trim()
                        }
                        offset += 18
                    }
                }

                // MAC address is at the end of the adapter status table (6 bytes)
                if (offset + 6 <= len) {
                    val macBytes = ByteArray(6)
                    System.arraycopy(buffer, offset, macBytes, 0, 6)
                    macStr = macBytes.joinToString(":") { "%02X".format(it) }
                }

                NetBiosResult(computerName, macStr)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            socket?.close()
        }
    }

    private fun queryLlmnr(ip: String): String? {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket()
            socket.soTimeout = 250

            // LLMNR standard query packet
            val query = byteArrayOf(
                0x00, 0x01, // ID
                0x00, 0x00, // Standard query
                0x00, 0x01, // Questions = 1
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x09, 'l'.code.toByte(), 'o'.code.toByte(), 'c'.code.toByte(), 'a'.code.toByte(), 'l'.code.toByte(), 'h'.code.toByte(), 'o'.code.toByte(), 's'.code.toByte(), 't'.code.toByte(),
                0x00,
                0x00, 0x01, // Type A
                0x00, 0x01  // Class IN
            )

            val packet = DatagramPacket(query, query.size, InetAddress.getByName(ip), 5355)
            socket.send(packet)

            val buffer = ByteArray(512)
            val recvPacket = DatagramPacket(buffer, buffer.size)
            socket.receive(recvPacket)

            if (recvPacket.length > 12) {
                "LLMNR-Responder"
            } else null
        } catch (_: Exception) {
            null
        } finally {
            socket?.close()
        }
    }

    private fun querySnmp(ip: String): String? {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket()
            socket.soTimeout = 300

            // SNMP v1 GetNextRequest for sysDescr (1.3.6.1.2.1.1.1.0) with community 'public'
            val snmpRequest = byteArrayOf(
                0x30, 0x29, // SEQUENCE
                0x02, 0x01, 0x00, // version 1 (0)
                0x04, 0x06, 'p'.code.toByte(), 'u'.code.toByte(), 'b'.code.toByte(), 'l'.code.toByte(), 'i'.code.toByte(), 'c'.code.toByte(), // community
                0xa1.toByte(), 0x1c, // GetNextRequest-PDU
                0x02, 0x04, 0x01, 0x02, 0x03, 0x04, // request-id
                0x02, 0x01, 0x00, // error-status (noError)
                0x02, 0x01, 0x00, // error-index (0)
                0x30, 0x0e, // VarbindList
                0x30, 0x0c, // Varbind
                0x06, 0x08, 0x2b, 0x06, 0x01, 0x02, 0x01, 0x01, 0x01, 0x00, // OID 1.3.6.1.2.1.1.1.0
                0x05, 0x00 // NULL
            )

            val packet = DatagramPacket(snmpRequest, snmpRequest.size, InetAddress.getByName(ip), 161)
            socket.send(packet)

            val buffer = ByteArray(1024)
            val recvPacket = DatagramPacket(buffer, buffer.size)
            socket.receive(recvPacket)

            if (recvPacket.length > 20) {
                String(buffer, 0, recvPacket.length)
            } else null
        } catch (_: Exception) {
            null
        } finally {
            socket?.close()
        }
    }

    private fun parseSnmpVendor(raw: String): String {
        val lower = raw.lowercase()
        return when {
            lower.contains("cisco") -> "Cisco Device"
            lower.contains("mikrotik") -> "MikroTik Router"
            lower.contains("synology") -> "Synology NAS"
            lower.contains("hp") || lower.contains("hewlett") -> "HP Printer / Server"
            lower.contains("canon") -> "Canon Device"
            lower.contains("epson") -> "Epson Printer"
            lower.contains("linux") -> "Linux Device"
            lower.contains("windows") -> "Windows Host"
            else -> "SNMP SNMPv1/v2 Device"
        }
    }

    private fun ensureOuiLoaded() {
        try {
            context.resources.openRawResource(com.networkanalyst.app.R.raw.oui_database).use {
                OuiLookupHelper.initializeIfNeeded(it)
            }
        } catch (_: Exception) {}
    }

    private fun guessVendor(mac: String?, hostname: String?, openPorts: List<Int>, isGateway: Boolean): String {
        if (isGateway) return "Router / Access Point"

        // OUI MAC prefix lookup (hỗ trợ MA-L, MA-M, MA-S và phát hiện MAC ngẫu nhiên / Private MAC)
        if (mac != null) {
            ensureOuiLoaded()
            val ouiVendor = OuiLookupHelper.lookupVendor(mac)
            if (ouiVendor != null) {
                return ouiVendor
            }
        }

        val host = hostname?.lowercase() ?: ""

        if (host.contains("iphone") || host.contains("ipad") || host.contains("apple") || host.contains("macbook")) {
            return "Apple Inc."
        }
        if (host.contains("galaxy") || host.contains("samsung")) {
            return "Samsung Electronics"
        }
        if (host.contains("chromecast") || host.contains("google")) {
            return "Google Device"
        }
        if (host.contains("printer") || host.contains("canon") || host.contains("epson") || openPorts.contains(9100)) {
            return "Network Printer"
        }
        if (openPorts.contains(445) || openPorts.contains(139) || openPorts.contains(3389)) {
            return "Windows PC / Server"
        }
        if (openPorts.contains(22) && !openPorts.contains(445)) {
            return "Linux / Unix Server"
        }

        if (mac != null) {
            return "Network Device (${mac.take(8)})"
        }

        return "Network Device"
    }

    private fun guessVendorFromMdns(type: String, serviceName: String): String {
        return when {
            type.contains("googlecast") -> "Google Chromecast / TV"
            type.contains("airplay") -> "Apple AirPlay Device"
            type.contains("ipp") || type.contains("printer") -> "Network Printer"
            type.contains("workstation") -> "Computer / Workstation"
            else -> serviceName
        }
    }

    private fun ipToLong(ip: String): Long {
        return try {
            val parts = ip.split(".")
            var result = 0L
            for (p in parts) {
                result = (result shl 8) + p.toInt()
            }
            result
        } catch (_: Exception) {
            0L
        }
    }

    private fun acquireMulticastLock() {
        try {
            if (multicastLock == null) {
                multicastLock = wifiManager?.createMulticastLock("network_analyst_multicast_lock")
                multicastLock?.setReferenceCounted(true)
            }
            multicastLock?.acquire()
        } catch (_: Exception) {}
    }

    private fun releaseMulticastLock() {
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (_: Exception) {}
    }

    companion object {
        fun isPartialSubnet(prefix: Int): Boolean = prefix in 1..23

        internal fun generateSubnetIps(ip: String, prefix: Int): List<String> {
            return try {
                if (prefix >= 31 || prefix <= 0) {
                    return emptyList()
                }
                val parts = ip.split(".").map { it.toInt() }
                if (parts.size != 4 || parts.any { it !in 0..255 }) {
                    return emptyList()
                }
                val ipLong = (parts[0].toLong() shl 24) or (parts[1].toLong() shl 16) or (parts[2].toLong() shl 8) or parts[3].toLong()
                val mask = (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
                val network = ipLong and mask
                val broadcast = network or (mask.inv() and 0xFFFFFFFFL)

                val start: Long
                val end: Long

                if (prefix >= 24) {
                    // Full subnet scan for /24 to /30
                    start = network + 1
                    end = broadcast - 1
                } else {
                    // Large subnet (< /24): limit to at most 254 IPs around current IP
                    var candidateStart = (ipLong - 127L).coerceAtLeast(network + 1)
                    var candidateEnd = (candidateStart + 254L).coerceAtMost(broadcast - 1)
                    if (candidateEnd - candidateStart < 254L) {
                        candidateStart = (candidateEnd - 254L).coerceAtLeast(network + 1)
                    }
                    start = candidateStart
                    end = candidateEnd
                }

                if (start > end) return emptyList()

                val ips = ArrayList<String>((end - start + 1).toInt().coerceAtLeast(0))
                for (curr in start..end) {
                    if (curr != ipLong) {
                        val ipStr = "${(curr ushr 24) and 0xff}.${(curr ushr 16) and 0xff}.${(curr ushr 8) and 0xff}.${curr and 0xff}"
                        ips.add(ipStr)
                    }
                }
                ips
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
