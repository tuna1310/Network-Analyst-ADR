package com.networkanalyst.app.data.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GeoIpParserTest {

    @Test
    fun parseResponse_successfulResponse_extractsLocation() {
        val sampleJson = """
        {
          "ip": "8.8.8.8",
          "success": true,
          "country": "United States",
          "country_code": "US",
          "city": "Mountain View",
          "connection": {
            "asn": 15169,
            "isp": "Google LLC"
          }
        }
        """.trimIndent()

        val info = GeoIpParser.parseResponse("8.8.8.8", sampleJson)

        assertEquals("8.8.8.8", info.ip)
        assertEquals("United States", info.country)
        assertEquals("US", info.countryCode)
        assertEquals("Mountain View", info.city)
        assertEquals("Google LLC", info.isp)
    }

    @Test
    fun parseResponse_failedResponse_returnsUnknown() {
        val sampleJson = """
        {
          "ip": "10.0.0.1",
          "success": false,
          "message": "reserved range"
        }
        """.trimIndent()

        val info = GeoIpParser.parseResponse("10.0.0.1", sampleJson)

        assertEquals("10.0.0.1", info.ip)
        assertNull(info.country)
        assertNull(info.countryCode)
    }

    @Test
    fun parseResponse_malformedJson_returnsUnknown() {
        val info = GeoIpParser.parseResponse("1.1.1.1", "invalid json response")

        assertEquals("1.1.1.1", info.ip)
        assertNull(info.country)
    }
}
