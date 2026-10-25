package com.networkanalyst.app.data.service

object NetworkClassifierHelper {

    // Hằng số phân loại bảo mật Wi-Fi (Tương thích WifiInfo.SECURITY_TYPE_* trên Android 11+)
    const val SECURITY_TYPE_OPEN = 0
    const val SECURITY_TYPE_WEP = 1
    const val SECURITY_TYPE_PSK = 2
    const val SECURITY_TYPE_EAP = 3
    const val SECURITY_TYPE_SAE = 4
    const val SECURITY_TYPE_EAP_WPA3_ENTERPRISE = 5
    const val SECURITY_TYPE_OWE = 6

    // Hằng số phân loại mạng di động (Tương thích TelephonyManager.NETWORK_TYPE_*)
    const val NETWORK_TYPE_GPRS = 1
    const val NETWORK_TYPE_EDGE = 2
    const val NETWORK_TYPE_UMTS = 3
    const val NETWORK_TYPE_HSDPA = 8
    const val NETWORK_TYPE_HSUPA = 9
    const val NETWORK_TYPE_HSPA = 10
    const val NETWORK_TYPE_LTE = 13
    const val NETWORK_TYPE_HSPAP = 15
    const val NETWORK_TYPE_NR = 20 // 5G

    /**
     * Phân loại chuẩn bảo mật Wi-Fi từ chuỗi capabilities (ScanResult) hoặc SecurityType (API 30+).
     */
    fun classifyWifiSecurity(capabilities: String? = null, securityTypeApi30: Int? = null): String {
        if (securityTypeApi30 != null && securityTypeApi30 >= 0) {
            return when (securityTypeApi30) {
                SECURITY_TYPE_OPEN -> "Open"
                SECURITY_TYPE_WEP -> "WEP"
                SECURITY_TYPE_PSK -> "WPA2-PSK / WPA-Personal"
                SECURITY_TYPE_EAP -> "WPA2-Enterprise (802.1X)"
                SECURITY_TYPE_SAE -> "WPA3-Personal (SAE)"
                SECURITY_TYPE_EAP_WPA3_ENTERPRISE -> "WPA3-Enterprise"
                SECURITY_TYPE_OWE -> "Enhanced Open (OWE)"
                else -> "WPA / WPA2 / WPA3"
            }
        }

        val cap = capabilities?.uppercase()?.trim() ?: return "Unknown"
        return when {
            cap.contains("SAE") && (cap.contains("PSK") || cap.contains("WPA2")) -> "WPA2 / WPA3 Mixed Mode"
            cap.contains("SAE") -> "WPA3-Personal (SAE)"
            cap.contains("WPA2-EAP") || cap.contains("WPA-EAP") -> "WPA2-Enterprise"
            cap.contains("WPA2") && cap.contains("WPA-") -> "WPA / WPA2-PSK"
            cap.contains("WPA2") -> "WPA2-PSK"
            cap.contains("WPA") -> "WPA-PSK"
            cap.contains("WEP") -> "WEP"
            cap.contains("OWE") -> "Enhanced Open (OWE)"
            cap.isEmpty() || cap == "[ESS]" -> "Open"
            else -> "Security: $cap"
        }
    }

    /**
     * Phân loại công nghệ mạng di động từ TelephonyManager network type.
     */
    fun classifyCellularType(networkType: Int): String {
        return when (networkType) {
            NETWORK_TYPE_NR -> "5G (NR - New Radio)"
            NETWORK_TYPE_LTE -> "4G (LTE)"
            NETWORK_TYPE_HSPAP, NETWORK_TYPE_HSPA, NETWORK_TYPE_HSDPA, NETWORK_TYPE_HSUPA, NETWORK_TYPE_UMTS ->
                "3G (HSPA+ / UMTS)"
            NETWORK_TYPE_EDGE, NETWORK_TYPE_GPRS ->
                "2G (EDGE / GPRS)"
            else -> "Cellular (Unknown generation)"
        }
    }

    /**
     * Phân loại phương thức truyền tải (Transport).
     */
    fun classifyTransport(
        isWifi: Boolean,
        isCellular: Boolean,
        isEthernet: Boolean,
        isVpn: Boolean
    ): String {
        return when {
            isVpn && isEthernet -> "VPN (over USB Ethernet)"
            isVpn && isWifi -> "VPN (over Wi-Fi)"
            isVpn && isCellular -> "VPN (over Cellular)"
            isVpn -> "Virtual Private Network (VPN)"
            isEthernet -> "Wired (USB Ethernet)"
            isWifi -> "Wi-Fi Network"
            isCellular -> "Mobile Data"
            else -> "Disconnected"
        }
    }
}
