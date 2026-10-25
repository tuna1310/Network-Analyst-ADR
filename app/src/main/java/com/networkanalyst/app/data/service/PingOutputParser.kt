package com.networkanalyst.app.data.service

import com.networkanalyst.app.R
import com.networkanalyst.app.data.model.PingPacket
import com.networkanalyst.app.ui.components.UiText
import java.util.regex.Pattern

data class RttStats(
    val minMs: Double,
    val avgMs: Double,
    val maxMs: Double,
    val mdevMs: Double
)

data class PingSummary(
    val transmitted: Int,
    val received: Int,
    val lossPercent: Double
)

object PingOutputParser {

    // Regex bắt các gói tin trả về từ lệnh ping hệ thống (hỗ trợ Linux / Toybox / Android iputils)
    // Ví dụ: "64 bytes from 8.8.8.8: icmp_seq=1 ttl=116 time=14.2 ms"
    // hoặc: "64 bytes from 8.8.8.8: seq=1 ttl=116 time=14.2 ms"
    // hoặc: "64 bytes from 192.168.1.1: icmp_seq=1 ttl=64 time<1 ms"
    private val PACKET_PATTERN = Pattern.compile(
        """(\d+)\s+bytes\s+from\s+([0-9a-fA-F:.]+):(?:\s+icmp_seq=(\d+)|\s+seq=(\d+))?\s+ttl=(\d+)\s+time(?:=([\d.]+)|<(\d+))\s*ms""",
        Pattern.CASE_INSENSITIVE
    )

    // Regex bắt thông số thống kê RTT cuối cùng
    // Ví dụ: "rtt min/avg/max/mdev = 13.914/14.502/15.201/0.512 ms"
    // hoặc: "round-trip min/avg/max = 13.9/14.5/15.2 ms"
    private val RTT_PATTERN = Pattern.compile(
        """(?:rtt|round-trip)\s+(?:min/avg/max(?:/mdev)?)\s*=\s*([\d.]+)/([\d.]+)/([\d.]+)(?:/([\d.]+))?\s*ms""",
        Pattern.CASE_INSENSITIVE
    )

    // Regex bắt số lượng gói gửi/nhận
    // Ví dụ: "4 packets transmitted, 4 received, 0% packet loss"
    private val SUMMARY_PATTERN = Pattern.compile(
        """(\d+)\s+packets\s+transmitted,\s+(\d+)\s+(?:packets\s+)?received,\s+([\d.]+)%\s+packet\s+loss""",
        Pattern.CASE_INSENSITIVE
    )

    fun parsePacketLine(line: String, targetIp: String = "", defaultSeq: Int = 1): PingPacket? {
        val matcher = PACKET_PATTERN.matcher(line)
        if (matcher.find()) {
            val bytes = matcher.group(1)?.toIntOrNull() ?: 64
            val matchedIp = matcher.group(2) ?: ""
            val ip = if (matchedIp.isNotBlank()) matchedIp else targetIp
            val seq = matcher.group(3)?.toIntOrNull() ?: matcher.group(4)?.toIntOrNull() ?: defaultSeq
            val ttl = matcher.group(5)?.toIntOrNull() ?: 64
            val time = matcher.group(6)?.toDoubleOrNull() ?: if (matcher.group(7) != null) 0.5 else 0.0

            return PingPacket(
                sequence = if (seq > 0) seq else defaultSeq,
                bytes = bytes,
                ip = ip,
                rttMs = time,
                ttl = ttl,
                isSuccess = true
            )
        }
        return null
    }

    fun parseSummaryLine(line: String): PingSummary? {
        val matcher = SUMMARY_PATTERN.matcher(line)
        if (matcher.find()) {
            val transmitted = matcher.group(1)?.toIntOrNull() ?: 0
            val received = matcher.group(2)?.toIntOrNull() ?: 0
            val loss = matcher.group(3)?.toDoubleOrNull() ?: 0.0
            return PingSummary(transmitted, received, loss)
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
     * Phân tích thông điệp lỗi từ stderr hoặc exit code thành UiText gói resource hoặc dynamic string.
     */
    fun parseErrorMessage(rawError: String): UiText {
        val lower = rawError.lowercase()
        return when {
            lower.contains("unknown host") || lower.contains("name or service not known") ->
                UiText.StringResource(R.string.err_dns_nxdomain)
            lower.contains("network is unreachable") || lower.contains("connect: network is unreachable") ->
                UiText.StringResource(R.string.err_ping_network_unreachable)
            lower.contains("operation not permitted") || lower.contains("permission denied") ->
                UiText.StringResource(R.string.err_ping_permission_denied)
            lower.contains("100% packet loss") || lower.contains("timed out") || lower.contains("timeout") ->
                UiText.StringResource(R.string.err_ping_packet_loss)
            rawError.isBlank() ->
                UiText.StringResource(R.string.err_ping_no_response)
            else ->
                UiText.DynamicString(rawError.trim())
        }
    }
}
