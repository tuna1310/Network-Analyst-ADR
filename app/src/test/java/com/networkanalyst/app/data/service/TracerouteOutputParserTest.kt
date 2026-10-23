package com.networkanalyst.app.data.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TracerouteOutputParserTest {

    @Test
    fun parseHopOutput_ttlExceeded_parsesHopIp() {
        val lines = listOf(
            "PING 8.8.8.8 (8.8.8.8) 56(84) bytes of data.",
            "From 192.168.1.1: icmp_seq=1 Time to live exceeded",
            "--- 8.8.8.8 ping statistics ---",
            "1 packets transmitted, 0 received, +1 errors, 100% packet loss"
        )

        val hop = TracerouteOutputParser.parseHopOutput(
            hopNumber = 1,
            outputLines = lines,
            targetIp = "8.8.8.8",
            fallbackElapsedMs = 5.0
        )

        assertEquals(1, hop.hopNumber)
        assertEquals("192.168.1.1", hop.ip)
        assertFalse(hop.isDestinationReached)
        assertFalse(hop.isTimeout)
    }

    @Test
    fun parseHopOutput_ttlExceededWithHostname_parsesIp() {
        val lines = listOf(
            "From router.local (10.0.0.1) icmp_seq=1 time exceeded"
        )

        val hop = TracerouteOutputParser.parseHopOutput(
            hopNumber = 2,
            outputLines = lines,
            targetIp = "8.8.8.8",
            fallbackElapsedMs = 12.0
        )

        assertEquals(2, hop.hopNumber)
        assertTrue(hop.ip == "router.local" || hop.ip == "10.0.0.1")
        assertFalse(hop.isDestinationReached)
        assertFalse(hop.isTimeout)
    }

    @Test
    fun parseHopOutput_destinationReached_marksDestinationTrue() {
        val lines = listOf(
            "64 bytes from 8.8.8.8: icmp_seq=1 ttl=117 time=14.2 ms",
            "--- 8.8.8.8 ping statistics ---",
            "1 packets transmitted, 1 received, 0% packet loss"
        )

        val hop = TracerouteOutputParser.parseHopOutput(
            hopNumber = 8,
            outputLines = lines,
            targetIp = "8.8.8.8",
            fallbackElapsedMs = 15.0
        )

        assertEquals(8, hop.hopNumber)
        assertEquals("8.8.8.8", hop.ip)
        assertEquals(14.2, hop.rttMs!!, 0.001)
        assertTrue(hop.isDestinationReached)
        assertFalse(hop.isTimeout)
    }

    @Test
    fun parseHopOutput_timeout_marksTimeoutTrue() {
        val lines = listOf(
            "--- 8.8.8.8 ping statistics ---",
            "1 packets transmitted, 0 received, 100% packet loss"
        )

        val hop = TracerouteOutputParser.parseHopOutput(
            hopNumber = 3,
            outputLines = lines,
            targetIp = "8.8.8.8",
            fallbackElapsedMs = 2000.0
        )

        assertEquals(3, hop.hopNumber)
        assertTrue(hop.isTimeout)
        assertFalse(hop.isDestinationReached)
        assertEquals(null, hop.ip)
    }

    @Test
    fun isTtlFlagUnsupported_detectsOemFlagErrors() {
        val invalidOptionLines = listOf("ping: invalid option -- 't'")
        assertTrue(TracerouteOutputParser.isTtlFlagUnsupported(invalidOptionLines))

        val unrecognizedLines = listOf("ping: unrecognized option '-t'")
        assertTrue(TracerouteOutputParser.isTtlFlagUnsupported(unrecognizedLines))

        val usageLines = listOf("usage: ping [-aAbBdDfhLnOqrRUvV6] [-c count] host")
        assertTrue(TracerouteOutputParser.isTtlFlagUnsupported(usageLines))

        val normalLines = listOf(
            "PING 8.8.8.8 (8.8.8.8) 56(84) bytes of data.",
            "From 192.168.1.1: icmp_seq=1 Time to live exceeded"
        )
        assertFalse(TracerouteOutputParser.isTtlFlagUnsupported(normalLines))
    }
}
