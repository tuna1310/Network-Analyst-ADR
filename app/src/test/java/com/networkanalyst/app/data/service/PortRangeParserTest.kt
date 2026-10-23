package com.networkanalyst.app.data.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortRangeParserTest {

    @Test
    fun parsePorts_singleAndList_parsesCorrectly() {
        val result = PortRangeParser.parsePorts("80, 443, 8080")
        assertEquals(listOf(80, 443, 8080), result)
    }

    @Test
    fun parsePorts_range_expandsCorrectly() {
        val result = PortRangeParser.parsePorts("8000-8005")
        assertEquals(listOf(8000, 8001, 8002, 8003, 8004, 8005), result)
    }

    @Test
    fun parsePorts_combinationWithSpacesAndSemicolon_deduplicatesAndSorts() {
        val result = PortRangeParser.parsePorts("443; 80, 80-82 22; 80")
        assertEquals(listOf(22, 80, 81, 82, 443), result)
    }

    @Test
    fun parsePorts_invalidAndOutOfBound_ignoresSafely() {
        val result = PortRangeParser.parsePorts("abc, -5, 0, 80, 70000, 443")
        assertEquals(listOf(80, 443), result)
    }

    @Test
    fun parsePorts_emptyOrWhitespace_returnsEmpty() {
        assertTrue(PortRangeParser.parsePorts("").isEmpty())
        assertTrue(PortRangeParser.parsePorts("   ").isEmpty())
    }

    @Test
    fun isPrivateIp_recognizesPrivateAndPublicIps() {
        // RFC 1918 Private
        assertTrue(PortRangeParser.isPrivateIp("192.168.1.1"))
        assertTrue(PortRangeParser.isPrivateIp("10.0.0.1"))
        assertTrue(PortRangeParser.isPrivateIp("172.16.0.1"))
        assertTrue(PortRangeParser.isPrivateIp("172.31.255.255"))

        // Loopback & Link-local
        assertTrue(PortRangeParser.isPrivateIp("127.0.0.1"))
        assertTrue(PortRangeParser.isPrivateIp("localhost"))
        assertTrue(PortRangeParser.isPrivateIp("169.254.10.20"))

        // CGNAT (100.64.0.0/10)
        assertTrue(PortRangeParser.isPrivateIp("100.64.0.5"))
        assertTrue(PortRangeParser.isPrivateIp("100.127.255.255"))

        // IPv6 Loopback, Link-Local & ULA
        assertTrue(PortRangeParser.isPrivateIp("::1"))
        assertTrue(PortRangeParser.isPrivateIp("fe80::1"))
        assertTrue(PortRangeParser.isPrivateIp("fc00::abcd"))
        assertTrue(PortRangeParser.isPrivateIp("fd12:3456::1"))

        // Public IPs
        assertFalse(PortRangeParser.isPrivateIp("8.8.8.8"))
        assertFalse(PortRangeParser.isPrivateIp("1.1.1.1"))
        assertFalse(PortRangeParser.isPrivateIp("172.32.0.1"))
        assertFalse(PortRangeParser.isPrivateIp("142.250.190.46"))
        assertFalse(PortRangeParser.isPrivateIp("2606:4700:4700::1111"))
    }

    @Test
    fun estimateDurationSeconds_calculatesBatchesAccurately() {
        // 200 ports, concurrency 200, timeout 200ms -> 1 batch = 0.2s -> rounds to 1s
        val est1 = PortRangeParser.estimateDurationSeconds(200, 200, 200)
        assertEquals(1, est1)

        // 65535 ports, concurrency 200, timeout 200ms -> 328 batches * 200ms = 65.6s -> 66s
        val estFullLan = PortRangeParser.estimateDurationSeconds(65535, 200, 200)
        assertEquals(66, estFullLan)

        // 65535 ports, concurrency 200, timeout 500ms -> 328 batches * 500ms = 164s
        val estFullWan = PortRangeParser.estimateDurationSeconds(65535, 200, 500)
        assertEquals(164, estFullWan)
    }
}
