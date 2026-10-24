package com.networkanalyst.app.data.service

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoIpPrivateIpFilterTest {

    @Test
    fun geoIpLookup_privateAndLocalIps_returnsPrivateWithoutNetworkCall() = runBlocking {
        val geoIpService = GeoIpService()

        // RFC 1918 Private IPs
        val infoLan = geoIpService.lookup("192.168.1.1")
        assertEquals("Private / LAN", infoLan.country)
        assertEquals("LAN", infoLan.countryCode)
        assertTrue(infoLan.isPrivate)

        val info10 = geoIpService.lookup("10.0.0.1")
        assertEquals("Private / LAN", info10.country)
        assertTrue(info10.isPrivate)

        // Loopback & Link-local
        val infoLoopback = geoIpService.lookup("127.0.0.1")
        assertEquals("Private / LAN", infoLoopback.country)
        assertTrue(infoLoopback.isPrivate)

        val infoLinkLocal = geoIpService.lookup("169.254.1.1")
        assertEquals("Private / LAN", infoLinkLocal.country)
        assertTrue(infoLinkLocal.isPrivate)

        // CGNAT (100.64.0.0/10)
        val infoCgnat = geoIpService.lookup("100.64.10.5")
        assertEquals("Private / LAN", infoCgnat.country)
        assertTrue(infoCgnat.isPrivate)

        // Star / Timeout
        val infoTimeout = geoIpService.lookup("* * *")
        assertEquals("* * *", infoTimeout.ip)
        assertEquals(null, infoTimeout.country)
    }
}
