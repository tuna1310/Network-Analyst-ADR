package com.networkanalyst.app.data.model

enum class ToolType(val displayName: String) {
    NETWORK_INFO("Thông tin mạng"),
    LAN_SCAN("Quét mạng LAN"),
    PING("Ping"),
    TRACEROUTE("Traceroute"),
    PORT_SCAN("Quét cổng"),
    DNS("Tra cứu DNS"),
    WHOIS("Whois / RDAP")
}

data class NetworkInfoData(
    val isConnected: Boolean = false,
    val connectionType: String = "Không có kết nối",
    val isWifi: Boolean = false,
    val isCellular: Boolean = false,
    val isVpn: Boolean = false,
    val isHotspotActive: Boolean = false,
    
    // Wi-Fi Details
    val ssid: String? = null,
    val bssid: String? = null,
    val rssi: Int? = null,
    val signalPercent: Int? = null,
    val linkSpeedMbps: Int? = null,
    val frequencyMhz: Int? = null,
    val wifiStandard: String? = null,
    val wifiChannel: Int? = null,
    val requiresLocationPermission: Boolean = false,

    // IP & Routing
    val localIpv4: String? = null,
    val localIpv6: String? = null,
    val subnetMask: String? = null,
    val prefixLength: Int? = null,
    val broadcastAddress: String? = null,
    val gateway: String? = null,
    val dnsServers: List<String> = emptyList(),
    val dhcpServer: String? = null,
    val interfaceName: String? = null,

    // Public Internet
    val publicIp: String? = null,
    val ispName: String? = null,
    val publicLocation: String? = null,
    val isPublicIpLoading: Boolean = false
)

enum class DiscoveryProtocol {
    TCP_PROBE,
    MDNS,
    NETBIOS,
    LLMNR,
    SNMP,
    GATEWAY
}

data class LanDevice(
    val ip: String,
    val mac: String? = null,
    val hostname: String? = null,
    val vendor: String? = null,
    val responseTimeMs: Long? = null,
    val protocols: Set<DiscoveryProtocol> = emptySet(),
    val openPorts: List<Int> = emptyList(),
    val isGateway: Boolean = false,
    val isCurrentDevice: Boolean = false
)

data class PingPacket(
    val sequence: Int,
    val bytes: Int = 64,
    val ip: String,
    val rttMs: Double,
    val ttl: Int,
    val isSuccess: Boolean = true,
    val errorMessage: String? = null
)

data class PingSummary(
    val target: String,
    val ip: String? = null,
    val transmitted: Int = 0,
    val received: Int = 0,
    val lossPercentage: Double = 0.0,
    val minRttMs: Double = 0.0,
    val avgRttMs: Double = 0.0,
    val maxRttMs: Double = 0.0,
    val mdevRttMs: Double = 0.0
)

data class TracerouteHop(
    val hop: Int,
    val ip: String,
    val hostname: String? = null,
    val rttMs: Double? = null,
    val isTimeout: Boolean = false,
    val isDestination: Boolean = false,
    val geoCountry: String? = null,
    val geoCountryCode: String? = null
)

enum class PortState {
    OPEN,
    CLOSED,
    FILTERED,
    ERROR
}

data class PortScanItem(
    val port: Int,
    val state: PortState,
    val serviceName: String,
    val banner: String? = null,
    val responseTimeMs: Long? = null
)

enum class DnsRecordType {
    A,
    AAAA,
    CNAME,
    MX,
    TXT,
    NS,
    SOA,
    PTR,
    SRV,
    CAA,
    HTTPS,
    SVCB
}

data class DnsResolver(
    val name: String,
    val primaryIp: String,
    val secondaryIp: String? = null,
    val description: String
)

data class DnsRecordItem(
    val queryName: String,
    val type: DnsRecordType,
    val value: String,
    val ttlSeconds: Long,
    val priority: Int? = null,
    val rawText: String
)

enum class WhoisQueryType {
    DOMAIN,
    IP,
    ASN
}

enum class WhoisSource {
    RDAP_IANA,
    RDAP_ORG_FALLBACK,
    WHOIS_PORT_43
}

data class WhoisSummary(
    val entityName: String? = null,
    val registrar: String? = null,
    val createdDate: String? = null,
    val expiresDate: String? = null,
    val updatedDate: String? = null,
    val statuses: List<String> = emptyList(),
    val cidr: String? = null,
    val nameservers: List<String> = emptyList(),
    val contactEmail: String? = null,
    val asnNumber: String? = null,
    val asnName: String? = null
)

data class WhoisResult(
    val target: String,
    val queryType: WhoisQueryType,
    val source: WhoisSource,
    val summary: WhoisSummary,
    val rawContent: String,
    val queryTimeMs: Long
)

data class GeoIpInfo(
    val ip: String,
    val country: String? = null,
    val countryCode: String? = null,
    val city: String? = null,
    val isp: String? = null,
    val isPrivate: Boolean = false
)
