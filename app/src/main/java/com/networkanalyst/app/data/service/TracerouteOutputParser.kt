package com.networkanalyst.app.data.service

import java.util.regex.Pattern

data class ParsedHop(
    val hopNumber: Int,
    val ip: String?,
    val rttMs: Double?,
    val isDestinationReached: Boolean,
    val isTimeout: Boolean
)

object TracerouteOutputParser {

    // Regex 1: "From 192.168.1.1: icmp_seq=1 Time to live exceeded"
    // hoặc "From 10.0.0.1 (10.0.0.1) icmp_seq=1 time exceeded"
    private val TTL_EXCEEDED_PATTERN = Pattern.compile(
        """(?:From|from)\s+([a-zA-Z0-9.-]+)(?:\s+\([a-zA-Z0-9.-]+\))?[: ]+.*?(?:Time to live exceeded|time exceeded)""",
        Pattern.CASE_INSENSITIVE
    )

    // Regex 2: Destination reached: "64 bytes from 8.8.8.8: icmp_seq=1 ttl=117 time=14.2 ms"
    private val DEST_REACHED_PATTERN = Pattern.compile(
        """(?:bytes\s+from\s+([a-zA-Z0-9.-]+):.*?time[=<]([0-9.]+)\s*ms)""",
        Pattern.CASE_INSENSITIVE
    )

    // Regex 3: Latency fallback
    private val RTT_PATTERN = Pattern.compile(
        """time[=<]([0-9.]+)\s*ms""",
        Pattern.CASE_INSENSITIVE
    )

    fun parseHopOutput(
        hopNumber: Int,
        outputLines: List<String>,
        targetIp: String,
        fallbackElapsedMs: Double
    ): ParsedHop {
        var hopIp: String? = null
        var rtt: Double? = null
        var destReached = false

        for (line in outputLines) {
            val clean = line.trim()
            if (clean.isEmpty()) continue

            // 1. Kiểm tra có đến đích không
            val destMatch = DEST_REACHED_PATTERN.matcher(clean)
            if (destMatch.find()) {
                hopIp = destMatch.group(1)?.replace(":", "")?.trim() ?: targetIp
                rtt = destMatch.group(2)?.toDoubleOrNull()
                destReached = true
                break
            }

            // 2. Kiểm tra TTL exceeded
            val ttlMatch = TTL_EXCEEDED_PATTERN.matcher(clean)
            if (ttlMatch.find()) {
                hopIp = ttlMatch.group(1)?.replace(":", "")?.trim()
            }

            // 3. Tìm độ trễ nếu có
            if (rtt == null) {
                val rttMatch = RTT_PATTERN.matcher(clean)
                if (rttMatch.find()) {
                    rtt = rttMatch.group(1)?.toDoubleOrNull()
                }
            }
        }

        if (hopIp != null) {
            val finalRtt = rtt ?: fallbackElapsedMs
            val isFinalDest = destReached || hopIp == targetIp
            return ParsedHop(
                hopNumber = hopNumber,
                ip = hopIp,
                rttMs = finalRtt,
                isDestinationReached = isFinalDest,
                isTimeout = false
            )
        }

        return ParsedHop(
            hopNumber = hopNumber,
            ip = null,
            rttMs = null,
            isDestinationReached = false,
            isTimeout = true
        )
    }

    /**
     * Kiểm tra xem binary ping của hệ thống có hỗ trợ cờ '-t' (TTL) hay không.
     * Nhiều nhà sản xuất (OEM) như Samsung, Xiaomi hoặc các bản toybox rút gọn
     * có thể không hỗ trợ cờ '-t' hoặc yêu cầu cú pháp khác.
     */
    fun isTtlFlagUnsupported(outputLines: List<String>): Boolean {
        for (line in outputLines) {
            val lower = line.lowercase()
            if (lower.contains("invalid option") ||
                lower.contains("illegal option") ||
                lower.contains("unknown option") ||
                lower.contains("unrecognized option") ||
                lower.contains("bad option") ||
                (lower.contains("usage:") && lower.contains("ping"))
            ) {
                return true
            }
        }
        return false
    }
}
