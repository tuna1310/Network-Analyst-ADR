package com.networkanalyst.app.data.service

object PortRangeParser {

    /**
     * Phân tích chuỗi đầu vào thành danh sách các cổng hợp lệ (1..65535).
     * Hỗ trợ:
     * - Cổng đơn lẻ: "80"
     * - Danh sách phân cách bởi dấu phẩy/khoảng trắng: "80, 443, 8080"
     * - Dải cổng: "1-100", "8000-8010"
     * - Kết hợp: "21, 22, 80-85, 443, 8080"
     */
    fun parsePorts(input: String): List<Int> {
        val clean = input.trim()
        if (clean.isEmpty()) return emptyList()

        val result = mutableSetOf<Int>()
        val tokens = clean.split(",", ";", " ").filter { it.isNotBlank() }

        for (token in tokens) {
            val part = token.trim()
            if (part.contains("-")) {
                val rangeParts = part.split("-").map { it.trim() }
                if (rangeParts.size == 2) {
                    val start = rangeParts[0].toIntOrNull()
                    val end = rangeParts[1].toIntOrNull()
                    if (start != null && end != null) {
                        val min = start.coerceIn(1, 65535)
                        val max = end.coerceIn(1, 65535)
                        val from = minOf(min, max)
                        val to = maxOf(min, max)
                        for (p in from..to) {
                            result.add(p)
                        }
                    }
                }
            } else {
                val port = part.toIntOrNull()
                if (port != null && port in 1..65535) {
                    result.add(port)
                }
            }
        }

        return result.sorted()
    }

    /**
     * Nhận diện xem một địa chỉ IP có phải thuộc dải Private (RFC 1918),
     * Loopback (127.0.0.0/8, ::1), Link-local (169.254.0.0/16, fe80::/10) hoặc CGNAT (100.64.0.0/10) hay không.
     */
    fun isPrivateIp(ip: String): Boolean {
        val clean = ip.trim().lowercase()
        if (clean.isEmpty()) return false
        if (clean == "localhost" || clean == "::1" || clean.startsWith("127.")) return true
        if (clean.startsWith("10.")) return true
        if (clean.startsWith("192.168.")) return true
        if (clean.startsWith("169.254.")) return true

        // IPv6 Link-Local (fe80::/10) và ULA (fc00::/7)
        if (clean.startsWith("fe8") || clean.startsWith("fe9") ||
            clean.startsWith("fea") || clean.startsWith("feb") ||
            clean.startsWith("fc") || clean.startsWith("fd")
        ) {
            return true
        }

        // 172.16.0.0 - 172.31.255.255
        val parts = clean.split(".")
        if (parts.size == 4) {
            val first = parts[0].toIntOrNull()
            val second = parts[1].toIntOrNull()
            if (first == 172 && second != null && second in 16..31) {
                return true
            }
            // CGNAT: 100.64.0.0 - 100.127.255.255
            if (first == 100 && second != null && second in 64..127) {
                return true
            }
        }

        return false
    }

    /**
     * Ước tính thời gian quét dựa trên số cổng, độ song song (concurrency) và timeout (ms).
     * Công thức: ceil(portCount / concurrency) * timeoutMs
     */
    fun estimateDurationSeconds(portCount: Int, concurrency: Int, timeoutMs: Int): Int {
        if (portCount <= 0 || concurrency <= 0 || timeoutMs <= 0) return 0
        val batches = (portCount + concurrency - 1) / concurrency
        val totalMs = batches.toLong() * timeoutMs
        return ((totalMs + 999) / 1000).toInt()
    }
}
