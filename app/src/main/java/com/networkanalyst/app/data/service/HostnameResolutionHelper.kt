package com.networkanalyst.app.data.service

object HostnameResolutionHelper {

    /**
     * Xác định hostname tốt nhất theo thứ tự ưu tiên chuẩn:
     * 1. mDNS (Multicast DNS - Apple Bonjour, Smart TV, Google Cast, IoT)
     * 2. NetBIOS (UDP port 137 - Windows PC, Samba server, NAS)
     * 3. LLMNR (Link-Local Multicast Name Resolution qua UDP port 5355)
     * 4. Reverse DNS (PTR query từ DNS nội bộ hoặc router)
     *
     * Quy tắc làm sạch:
     * - Bỏ qua chuỗi null, rỗng, chỉ gồm khoảng trắng.
     * - Bỏ qua chuỗi nếu giá trị trả về trùng với chính địa chỉ IP (thường do resolver trả về khi không có PTR record).
     */
    fun selectBestHostname(
        ip: String,
        mdns: String? = null,
        netbios: String? = null,
        llmnr: String? = null,
        ptr: String? = null
    ): String? {
        val cleanMdns = cleanCandidate(mdns, ip)
        if (cleanMdns != null) return cleanMdns

        val cleanNetbios = cleanCandidate(netbios, ip)
        if (cleanNetbios != null) return cleanNetbios

        val cleanLlmnr = cleanCandidate(llmnr, ip)
        if (cleanLlmnr != null) return cleanLlmnr

        val cleanPtr = cleanCandidate(ptr, ip)
        if (cleanPtr != null) return cleanPtr

        return null
    }

    private fun cleanCandidate(candidate: String?, ip: String): String? {
        val trimmed = candidate?.trim() ?: return null
        if (trimmed.isEmpty() || trimmed.equals(ip.trim(), ignoreCase = true)) {
            return null
        }
        return trimmed
    }
}
