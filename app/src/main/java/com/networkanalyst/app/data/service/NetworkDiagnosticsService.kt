package com.networkanalyst.app.data.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.networkanalyst.app.data.model.NetworkInfoData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkDiagnosticsService @Inject constructor(
    @ApplicationContext private val context: Context
) : DiagnosticsHelper {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val _networkInfo = MutableStateFlow(NetworkInfoData())
    val networkInfo: StateFlow<NetworkInfoData> = _networkInfo.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        registerNetworkCallback()
        refreshNetworkInfo()
    }

    private fun registerNetworkCallback() {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            connectivityManager.registerNetworkCallback(
                request,
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        refreshNetworkInfo()
                    }

                    override fun onLost(network: Network) {
                        refreshNetworkInfo()
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        networkCapabilities: NetworkCapabilities
                    ) {
                        refreshNetworkInfo()
                    }

                    override fun onLinkPropertiesChanged(
                        network: Network,
                        linkProperties: LinkProperties
                    ) {
                        refreshNetworkInfo()
                    }
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun getNetworkInfoSnapshot(): NetworkInfoData {
        val current = _networkInfo.value
        return if (current.localIpv4 != null) current else collectLocalNetworkInfo()
    }

    fun refreshNetworkInfo() {
        scope.launch {
            val baseInfo = collectLocalNetworkInfo()
            _networkInfo.value = baseInfo
            // Fetch public IP in background
            fetchPublicIpDetails(baseInfo)
        }
    }

    @Suppress("DEPRECATION")
    private fun collectLocalNetworkInfo(): NetworkInfoData {
        val activeNetwork = connectivityManager.activeNetwork
        if (activeNetwork == null) {
            return NetworkInfoData(
                isConnected = false,
                connectionType = "Không có kết nối mạng"
            )
        }

        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val linkProperties = connectivityManager.getLinkProperties(activeNetwork)

        val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isCellular = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val isVpn = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        val isEthernet = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true

        val connectionType = when {
            isVpn && isWifi -> "VPN (trên Wi-Fi)"
            isVpn && isCellular -> "VPN (trên Di động)"
            isVpn -> "Mạng riêng ảo (VPN)"
            isWifi -> "Mạng Wi-Fi"
            isCellular -> "Dữ liệu di động"
            isEthernet -> "Mạng có dây (Ethernet)"
            else -> "Kết nối khác"
        }

        // Subnet, IP, Gateway, DNS
        var localIpv4: String? = null
        var localIpv6: String? = null
        var prefixLength: Int? = null
        var subnetMask: String? = null
        var broadcastAddress: String? = null

        linkProperties?.linkAddresses?.forEach { addr ->
            val inetAddr = addr.address
            if (inetAddr is Inet4Address && localIpv4 == null) {
                localIpv4 = inetAddr.hostAddress
                prefixLength = addr.prefixLength
                subnetMask = prefixLengthToSubnetMask(addr.prefixLength)
                broadcastAddress = inetAddr.hostAddress?.let { calculateBroadcast(it, addr.prefixLength) }
            } else if (inetAddr is Inet6Address && localIpv6 == null && !inetAddr.isLinkLocalAddress) {
                localIpv6 = inetAddr.hostAddress
            }
        }

        // Default Gateway
        var gateway: String? = null
        linkProperties?.routes?.forEach { route ->
            if (route.isDefaultRoute && route.gateway != null && route.gateway is Inet4Address) {
                gateway = route.gateway?.hostAddress
            }
        }
        if (gateway == null && isWifi) {
            val dhcp = wifiManager.dhcpInfo
            if (dhcp != null && dhcp.gateway != 0) {
                gateway = intToIp(dhcp.gateway)
            }
        }

        // DNS Servers
        val dnsServers = linkProperties?.dnsServers?.mapNotNull { it.hostAddress } ?: emptyList()

        // DHCP Server
        var dhcpServer: String? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            dhcpServer = linkProperties?.dhcpServerAddress?.hostAddress
        }
        if (dhcpServer == null && isWifi) {
            val dhcp = wifiManager.dhcpInfo
            if (dhcp != null && dhcp.serverAddress != 0) {
                dhcpServer = intToIp(dhcp.serverAddress)
            }
        }

        val interfaceName = linkProperties?.interfaceName

        // Hotspot check
        val isHotspot = checkIsHotspotActive()

        // Wi-Fi Details
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        var ssid: String? = null
        var bssid: String? = null
        var rssi: Int? = null
        var signalPercent: Int? = null
        var linkSpeedMbps: Int? = null
        var frequencyMhz: Int? = null
        var wifiStandard: String? = null
        var wifiChannel: Int? = null
        val requiresLocation = isWifi && !hasFineLocation

        if (isWifi) {
            val info: WifiInfo? = wifiManager.connectionInfo
            if (info != null) {
                rssi = info.rssi
                signalPercent = calculateSignalPercent(info.rssi)
                linkSpeedMbps = if (info.linkSpeed > 0) info.linkSpeed else null
                frequencyMhz = if (info.frequency > 0) info.frequency else null
                wifiChannel = frequencyMhz?.let { freqToChannel(it) }

                if (hasFineLocation) {
                    val rawSsid = info.ssid
                    ssid = if (rawSsid != null && rawSsid != "<unknown ssid>" && rawSsid != "") {
                        rawSsid.trim('"')
                    } else {
                        "Đã kết nối Wi-Fi"
                    }
                    bssid = if (info.bssid != null && info.bssid != "02:00:00:00:00:00") {
                        info.bssid
                    } else null
                } else {
                    ssid = "Cần quyền vị trí để xem tên Wi-Fi"
                    bssid = null
                }

                wifiStandard = when {
                    frequencyMhz in 5925..7125 -> "Wi-Fi 6E / Wi-Fi 7 (6 GHz)"
                    frequencyMhz in 4900..5900 -> "Wi-Fi 5 / Wi-Fi 6 (5 GHz)"
                    frequencyMhz in 2400..2500 -> "Wi-Fi 4 (2.4 GHz)"
                    else -> "Wi-Fi"
                }
            }
        }

        return NetworkInfoData(
            isConnected = true,
            connectionType = connectionType,
            isWifi = isWifi,
            isCellular = isCellular,
            isVpn = isVpn,
            isHotspotActive = isHotspot,
            ssid = ssid,
            bssid = bssid,
            rssi = rssi,
            signalPercent = signalPercent,
            linkSpeedMbps = linkSpeedMbps,
            frequencyMhz = frequencyMhz,
            wifiStandard = wifiStandard,
            wifiChannel = wifiChannel,
            requiresLocationPermission = requiresLocation,
            localIpv4 = localIpv4,
            localIpv6 = localIpv6,
            subnetMask = subnetMask,
            prefixLength = prefixLength,
            broadcastAddress = broadcastAddress,
            gateway = gateway,
            dnsServers = dnsServers,
            dhcpServer = dhcpServer,
            interfaceName = interfaceName,
            isPublicIpLoading = true
        )
    }

    private suspend fun fetchPublicIpDetails(current: NetworkInfoData) {
        if (!current.isConnected) {
            _networkInfo.value = current.copy(isPublicIpLoading = false)
            return
        }

        try {
            val result = withContext(Dispatchers.IO) {
                var ip: String? = null
                var isp: String? = null
                var location: String? = null

                // First attempt: ipify / ip-api
                try {
                    val url = URL("https://api.ipify.org?format=json")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000
                    conn.requestMethod = "GET"
                    if (conn.responseCode == 200) {
                        val reader = BufferedReader(InputStreamReader(conn.inputStream))
                        val json = JSONObject(reader.readText())
                        ip = json.optString("ip")
                        reader.close()
                    }
                    conn.disconnect()
                } catch (e: Exception) {
                    // Fallback to icanhazip
                    try {
                        val url = URL("https://icanhazip.com")
                        val conn = url.openConnection() as HttpURLConnection
                        conn.connectTimeout = 3000
                        conn.readTimeout = 3000
                        if (conn.responseCode == 200) {
                            val reader = BufferedReader(InputStreamReader(conn.inputStream))
                            ip = reader.readLine()?.trim()
                            reader.close()
                        }
                        conn.disconnect()
                    } catch (_: Exception) {}
                }

                // If public IP found, attempt geo/ISP lookup
                if (!ip.isNullOrBlank()) {
                    try {
                        val url = URL("https://ipwho.is/$ip")
                        val conn = url.openConnection() as HttpURLConnection
                        conn.connectTimeout = 3000
                        conn.readTimeout = 3000
                        if (conn.responseCode == 200) {
                            val reader = BufferedReader(InputStreamReader(conn.inputStream))
                            val json = JSONObject(reader.readText())
                            if (json.optBoolean("success", false)) {
                                val connection = json.optJSONObject("connection")
                                isp = connection?.optString("isp") ?: connection?.optString("org")
                                val city = json.optString("city")
                                val country = json.optString("country")
                                location = if (city.isNotEmpty() && country.isNotEmpty()) "$city, $country" else country
                            }
                            reader.close()
                        }
                        conn.disconnect()
                    } catch (_: Exception) {}
                }

                Triple(ip, isp, location)
            }

            _networkInfo.value = _networkInfo.value.copy(
                publicIp = result.first,
                ispName = result.second,
                publicLocation = result.third,
                isPublicIpLoading = false
            )
        } catch (e: Exception) {
            _networkInfo.value = _networkInfo.value.copy(isPublicIpLoading = false)
        }
    }

    private fun checkIsHotspotActive(): Boolean {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return false
            for (intf in interfaces) {
                if (intf.isUp && (intf.name.contains("ap") || intf.name.contains("rndis") || intf.name.contains("tether"))) {
                    return true
                }
            }
        } catch (_: Exception) {}
        return false
    }

    private fun calculateSignalPercent(rssi: Int): Int {
        return when {
            rssi <= -100 -> 0
            rssi >= -50 -> 100
            else -> 2 * (rssi + 100)
        }.coerceIn(0, 100)
    }

    private fun freqToChannel(freq: Int): Int {
        return when {
            freq == 2484 -> 14
            freq in 2412..2472 -> (freq - 2407) / 5
            freq in 5170..5825 -> (freq - 5000) / 5
            freq in 5955..7115 -> (freq - 5950) / 5
            else -> 0
        }
    }

    private fun prefixLengthToSubnetMask(prefix: Int): String {
        val mask = -0x1 shl (32 - prefix)
        return "" + (mask ushr 24 and 0xff) + "." +
                (mask ushr 16 and 0xff) + "." +
                (mask ushr 8 and 0xff) + "." +
                (mask and 0xff)
    }

    private fun calculateBroadcast(ip: String, prefix: Int): String? {
        return try {
            val parts = ip.split(".").map { it.toInt() }
            val ipInt = (parts[0] shl 24) or (parts[1] shl 16) or (parts[2] shl 8) or parts[3]
            val mask = -0x1 shl (32 - prefix)
            val bcast = ipInt or mask.inv()
            "" + (bcast ushr 24 and 0xff) + "." +
                    (bcast ushr 16 and 0xff) + "." +
                    (bcast ushr 8 and 0xff) + "." +
                    (bcast and 0xff)
        } catch (_: Exception) {
            null
        }
    }

    private fun intToIp(i: Int): String {
        return (i and 0xFF).toString() + "." +
                (i shr 8 and 0xFF) + "." +
                (i shr 16 and 0xFF) + "." +
                (i shr 24 and 0xFF)
    }
}
