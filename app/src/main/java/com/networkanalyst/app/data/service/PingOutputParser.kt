package com.networkanalyst.app.data.service

import com.networkanalyst.app.data.model.PingPacket
import com.networkanalyst.app.data.model.PingSummary
import java.util.regex.Pattern

object PingOutputParser {

    // Regex bắt các biến thể gói tin:
    // 1. "64 bytes from 8.8.8.8: icmp_seq=1 ttl=117 time=14.2 ms"
    // 2. "64 bytes from 8.8.8.8: seq=1 ttl=117 time=14.2 ms"
    // 3. "64 bytes from 8.8.8.8: icmp_seq=1 ttl=117 time<1 ms"
    private val PACKET_PATTERN = Pattern.compile(
        """(?:(\d+)\s+bytes\s+from\s+([^: ]+):?\s+(?:icmp_)?seq=(\d+)\s+ttl=(\d+)\s+time([=<])([0-9.]+)\s*ms)""",
        Pattern.CASE_INSENSITIVE
    )

    // Regex bắt dòng thống kê truyền nhận gói tin
    private val SUMMARY_PATTERN = Pattern.compile(
        """(\d+)\s+packets\s+transmitted,\s+(\d+)\s+(?:packets\s+)?received,.*?([0-9.]+)\s*%\s*(?:packet\s+)?loss""",
        Pattern.CASE_INSENSITIVE
    )

    // Regex bắt dòng thống kê độ trễ rtt
    private val RTT_PATTERN = Pattern.compile(
        """(?:rtt|round-trip)\s+(?:min/avg/max(?:/mdev)?)\s*=\s*([0-9.]+)/([0-9.]+)/([0-9.]+)(?:/([0-9.]+))?""",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Phân tích một dòng đầu ra từ lệnh ping để trích xuất thông tin gói tin.
     */
    fun parsePacketLine(line: String, defaultIp: String, fallbackSeq: Int): PingPacket? {
        val matcher = PACKET_PATTERN.matcher(line)
        if (matcher.find()) {
            val bytes = matcher.group(1)?.toIntOrNull() ?: 64
            val ip = matcher.group(2)?.trim() ?: defaultIp
            val seq = matcher.group(3)?.toIntOrNull() ?: fallbackSeq
            val ttl = matcher.group(4)?.toIntOrNull() ?: 64
            val op = matcher.group(5)
            val parsedTime = matcher.group(6)?.toDoubleOrNull() ?: 1.0
            val timeMs = if (op == "<") 0.5 else parsedTime

            return PingPacket(
                sequence = seq,
                bytes = bytes,
                ip = ip,
                rttMs = timeMs,
                ttl = ttl,
                isSuccess = true
            )
        }
        return null
    }

    data class SummaryStats(
        val transmitted: Int,
        val received: Int,
        val lossPercent: Double
    )

    data class RttStats(
        val minMs: Double,
        val avgMs: Double,
        val maxMs: Double,
        val mdevMs: Double
    )

    fun parseSummaryLine(line: String): SummaryStats? {
        val matcher = SUMMARY_PATTERN.matcher(line)
        if (matcher.find()) {
            val transmitted = matcher.group(1)?.toIntOrNull() ?: 0
            val received = matcher.group(2)?.toIntOrNull() ?: 0
            val loss = matcher.group(3)?.toDoubleOrNull() ?: 0.0
            return SummaryStats(transmitted, received, loss)
        }
        return null
    }

    fun parseRttLine(line: String): RttStats? {
        val matcher = RTT_PATTERN.matcher(line)
        if (matcher.find()) {
            val min = matcher.group(1)?.toDoubleOrNull() ?: 0.0
            val avg = matcher.group(2)?.toDoubleOrNull() ?: 0.0
            val max = matcher.group(3)?.toDoubleOrNull() ?: 0.0
            val mdev = matcher.group(4)?.toDoubleOrNull() ?: 0.0
            return RttStats(min, avg, max, mdev)
        }
        return null
    }

    /**
     * Phân tích thông điệp lỗi từ stderr hoặc exit code thành thông báo thân thiện.
     */
    fun parseErrorMessage(rawError: String): String {
        val lower = rawError.lowercase()
        return when {
            lower.contains("unknown host") || lower.contains("name or service not known") ->
                "Tên miền không tồn tại hoặc không thể phân giải DNS."
            lower.contains("network is unreachable") || lower.contains("connect: network is unreachable") ->
                "Mạng không khả dụng. Vui lòng kiểm tra lại kết nối Wi-Fi hoặc dữ liệu di động."
            lower.contains("operation not permitted") || lower.contains("permission denied") ->
                "Hệ điều hành chặn quyền raw socket ICMP. Ứng dụng sẽ chuyển sang cơ chế kiểm tra socket thay thế."
            lower.contains("100% packet loss") || lower.contains("timed out") || lower.contains("timeout") ->
                "Hết thời gian chờ phản hồi (Request Timed Out). Máy chủ đích có thể đang chặn gói tin ICMP."
            else -> rawError.trim().ifEmpty { "Không nhận được phản hồi từ máy chủ đích." }
        }
    }
}
