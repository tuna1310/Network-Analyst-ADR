package com.networkanalyst.app.data.service

import com.networkanalyst.app.R
import com.networkanalyst.app.ui.components.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kiểm thử bộ phân tích chuỗi đầu ra của lệnh ping.
 * Lưu ý: Toàn bộ mẫu đầu vào trong test suite này là fixture tự soạn theo định dạng chuẩn
 * của iputils và toybox (chưa được kiểm chứng thực tế trên từng thiết bị phần cứng của các hãng máy).
 */
class PingOutputParserTest {

    @Test
    fun parsePacketLine_standardLinux_parsesCorrectly() {
        val line = "64 bytes from 8.8.8.8: icmp_seq=1 ttl=117 time=14.2 ms"
        val packet = PingOutputParser.parsePacketLine(line, "8.8.8.8", 1)

        assertNotNull(packet)
        assertEquals(1, packet!!.sequence)
        assertEquals(64, packet.bytes)
        assertEquals("8.8.8.8", packet.ip)
        assertEquals(117, packet.ttl)
        assertEquals(14.2, packet.rttMs, 0.001)
        assertTrue(packet.isSuccess)
    }

    @Test
    fun parsePacketLine_androidToybox_parsesCorrectly() {
        val line = "64 bytes from 1.1.1.1: seq=3 ttl=56 time=22.5 ms"
        val packet = PingOutputParser.parsePacketLine(line, "1.1.1.1", 3)

        assertNotNull(packet)
        assertEquals(3, packet!!.sequence)
        assertEquals("1.1.1.1", packet.ip)
        assertEquals(56, packet.ttl)
        assertEquals(22.5, packet.rttMs, 0.001)
    }

    @Test
    fun parsePacketLine_subMillisecond_handlesLessThanOneMs() {
        val line = "64 bytes from 192.168.1.1: icmp_seq=1 ttl=64 time<1 ms"
        val packet = PingOutputParser.parsePacketLine(line, "192.168.1.1", 1)

        assertNotNull(packet)
        assertEquals(1, packet!!.sequence)
        assertEquals(0.5, packet.rttMs, 0.001)
    }

    @Test
    fun parsePacketLine_invalidLine_returnsNull() {
        assertNull(PingOutputParser.parsePacketLine("PING 8.8.8.8 (8.8.8.8) 56(84) bytes of data.", "8.8.8.8", 1))
        assertNull(PingOutputParser.parsePacketLine("Request timeout for icmp_seq 1", "8.8.8.8", 1))
    }

    @Test
    fun parseSummaryLine_variations_parsesTransmittedReceivedLoss() {
        val line1 = "4 packets transmitted, 4 received, 0% packet loss"
        val stats1 = PingOutputParser.parseSummaryLine(line1)
        assertNotNull(stats1)
        assertEquals(4, stats1!!.transmitted)
        assertEquals(4, stats1.received)
        assertEquals(0.0, stats1.lossPercent, 0.001)

        val line2 = "10 packets transmitted, 8 packets received, 20.0% packet loss"
        val stats2 = PingOutputParser.parseSummaryLine(line2)
        assertNotNull(stats2)
        assertEquals(10, stats2!!.transmitted)
        assertEquals(8, stats2.received)
        assertEquals(20.0, stats2.lossPercent, 0.001)
    }

    @Test
    fun parseRttLine_standardAndMdev_parsesCorrectly() {
        val line1 = "rtt min/avg/max/mdev = 12.1/15.3/18.4/2.1 ms"
        val rtt1 = PingOutputParser.parseRttLine(line1)
        assertNotNull(rtt1)
        assertEquals(12.1, rtt1!!.minMs, 0.001)
        assertEquals(15.3, rtt1.avgMs, 0.001)
        assertEquals(18.4, rtt1.maxMs, 0.001)
        assertEquals(2.1, rtt1.mdevMs, 0.001)

        val line2 = "round-trip min/avg/max = 10.0/15.0/20.0 ms"
        val rtt2 = PingOutputParser.parseRttLine(line2)
        assertNotNull(rtt2)
        assertEquals(10.0, rtt2!!.minMs, 0.001)
        assertEquals(15.0, rtt2.avgMs, 0.001)
        assertEquals(20.0, rtt2.maxMs, 0.001)
    }

    @Test
    fun parseErrorMessage_mapsFriendlyErrors() {
        val err1 = PingOutputParser.parseErrorMessage("ping: unknown host invalid-domain.test")
        assertTrue(err1 is UiText.StringResource && err1.resId == R.string.err_dns_nxdomain)

        val err2 = PingOutputParser.parseErrorMessage("connect: Network is unreachable")
        assertTrue(err2 is UiText.StringResource && err2.resId == R.string.err_ping_network_unreachable)

        val err3 = PingOutputParser.parseErrorMessage("ping: sendmsg: Operation not permitted")
        assertTrue(err3 is UiText.StringResource && err3.resId == R.string.err_ping_permission_denied)

        val err4 = PingOutputParser.parseErrorMessage("100% packet loss, Request Timed Out")
        assertTrue(err4 is UiText.StringResource && err4.resId == R.string.err_ping_packet_loss)

        val err5 = PingOutputParser.parseErrorMessage("ping: icmp open socket: Permission denied")
        assertTrue(err5 is UiText.StringResource && err5.resId == R.string.err_ping_permission_denied)
    }
}
