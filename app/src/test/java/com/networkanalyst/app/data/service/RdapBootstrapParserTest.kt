package com.networkanalyst.app.data.service

import com.networkanalyst.app.data.model.WhoisQueryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RdapBootstrapParserTest {

    // Fixture tự soạn mô phỏng cấu trúc dns.json của IANA
    private val dnsBootstrapJson = """
    {
      "description": "RDAP bootstrap file for Domain Name System registries",
      "publication": "2026-10-24T00:00:00Z",
      "services": [
        [
          ["com", "net"],
          ["https://rdap.verisign.com/com/v1/"]
        ],
        [
          ["org"],
          ["https://rdap.publicinterestregistry.org/rdap/"]
        ],
        [
          ["vn"],
          ["https://rdap.vnnic.vn/"]
        ]
      ],
      "version": "1.0"
    }
    """.trimIndent()

    // Fixture tự soạn mô phỏng cấu trúc ipv4.json của IANA
    private val ipv4BootstrapJson = """
    {
      "description": "RDAP bootstrap file for IPv4 Address Space",
      "publication": "2026-10-24T00:00:00Z",
      "services": [
        [
          ["1.0.0.0/8", "27.0.0.0/8"],
          ["https://rdap.apnic.net/"]
        ],
        [
          ["8.0.0.0/8"],
          ["https://rdap.arin.net/registry/"]
        ]
      ],
      "version": "1.0"
    }
    """.trimIndent()

    // Fixture tự soạn mô phỏng cấu trúc ipv6.json của IANA
    private val ipv6BootstrapJson = """
    {
      "description": "RDAP bootstrap file for IPv6 Address Space",
      "publication": "2026-10-24T00:00:00Z",
      "services": [
        [
          ["2001:200::/23", "2400::/12"],
          ["https://rdap.apnic.net/"]
        ],
        [
          ["2600::/12"],
          ["https://rdap.arin.net/registry/"]
        ]
      ],
      "version": "1.0"
    }
    """.trimIndent()

    // Fixture tự soạn mô phỏng cấu trúc asn.json của IANA
    private val asnBootstrapJson = """
    {
      "description": "RDAP bootstrap file for Autonomous System Number Registries",
      "publication": "2026-10-24T00:00:00Z",
      "services": [
        [
          ["1000-2000", "4000-5000"],
          ["https://rdap.arin.net/registry/"]
        ],
        [
          ["15169", "174-175"],
          ["https://rdap.arin.net/registry/"]
        ],
        [
          ["7545-7550"],
          ["https://rdap.apnic.net/"]
        ]
      ],
      "version": "1.0"
    }
    """.trimIndent()

    @Test
    fun findBootstrapUrl_dnsBootstrap_matchesTld() {
        val comUrl = WhoisRdapParser.findBootstrapUrl(dnsBootstrapJson, "google.com", WhoisQueryType.DOMAIN)
        assertEquals("https://rdap.verisign.com/com/v1/", comUrl)

        val orgUrl = WhoisRdapParser.findBootstrapUrl(dnsBootstrapJson, "wikipedia.org", WhoisQueryType.DOMAIN)
        assertEquals("https://rdap.publicinterestregistry.org/rdap/", orgUrl)

        val vnUrl = WhoisRdapParser.findBootstrapUrl(dnsBootstrapJson, "vnexpress.net.vn", WhoisQueryType.DOMAIN)
        assertEquals("https://rdap.vnnic.vn/", vnUrl)

        val unknownTld = WhoisRdapParser.findBootstrapUrl(dnsBootstrapJson, "test.unknown", WhoisQueryType.DOMAIN)
        assertNull(unknownTld)
    }

    @Test
    fun findBootstrapUrl_ipv4Bootstrap_matchesFirstOctet() {
        val apnicUrl = WhoisRdapParser.findBootstrapUrl(ipv4BootstrapJson, "1.1.1.1", WhoisQueryType.IP)
        assertEquals("https://rdap.apnic.net/", apnicUrl)

        val arinUrl = WhoisRdapParser.findBootstrapUrl(ipv4BootstrapJson, "8.8.8.8", WhoisQueryType.IP)
        assertEquals("https://rdap.arin.net/registry/", arinUrl)
    }

    @Test
    fun findBootstrapUrl_ipv6Bootstrap_matchesPrefix() {
        val apnicUrl = WhoisRdapParser.findBootstrapUrl(ipv6BootstrapJson, "2001:200:123::1", WhoisQueryType.IP)
        assertEquals("https://rdap.apnic.net/", apnicUrl)

        val arinUrl = WhoisRdapParser.findBootstrapUrl(ipv6BootstrapJson, "2600:1400::1", WhoisQueryType.IP)
        assertEquals("https://rdap.arin.net/registry/", arinUrl)
    }

    @Test
    fun findBootstrapUrl_asnBootstrap_matchesRangeAndSingle() {
        val rangeUrl = WhoisRdapParser.findBootstrapUrl(asnBootstrapJson, "AS1500", WhoisQueryType.ASN)
        assertEquals("https://rdap.arin.net/registry/", rangeUrl)

        val singleUrl = WhoisRdapParser.findBootstrapUrl(asnBootstrapJson, "15169", WhoisQueryType.ASN)
        assertEquals("https://rdap.arin.net/registry/", singleUrl)

        val apnicUrl = WhoisRdapParser.findBootstrapUrl(asnBootstrapJson, "AS7547", WhoisQueryType.ASN)
        assertEquals("https://rdap.apnic.net/", apnicUrl)

        val notFound = WhoisRdapParser.findBootstrapUrl(asnBootstrapJson, "AS999999", WhoisQueryType.ASN)
        assertNull(notFound)
    }

    @Test
    fun findBootstrapUrl_missingServicesField_returnsNullSafely() {
        val missingServicesJson = """{"description": "No services"}"""
        val result = WhoisRdapParser.findBootstrapUrl(missingServicesJson, "example.com", WhoisQueryType.DOMAIN)
        assertNull(result)
    }

    @Test
    fun findBootstrapUrl_corruptedJson_returnsNullSafely() {
        val corruptedJson = "{ not valid json at all: [[["
        val result = WhoisRdapParser.findBootstrapUrl(corruptedJson, "example.com", WhoisQueryType.DOMAIN)
        assertNull(result)
    }
}
