package com.networkanalyst.app.data.service

import com.networkanalyst.app.data.model.WhoisQueryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RdapParserTest {

    @Test
    fun determineQueryType_identifiesTypesCorrectly() {
        assertEquals(WhoisQueryType.DOMAIN, WhoisRdapParser.determineQueryType("google.com"))
        assertEquals(WhoisQueryType.DOMAIN, WhoisRdapParser.determineQueryType("vnnic.vn"))
        assertEquals(WhoisQueryType.IP, WhoisRdapParser.determineQueryType("8.8.8.8"))
        assertEquals(WhoisQueryType.IP, WhoisRdapParser.determineQueryType("2001:4860:4860::8888"))
        assertEquals(WhoisQueryType.ASN, WhoisRdapParser.determineQueryType("AS15169"))
        assertEquals(WhoisQueryType.ASN, WhoisRdapParser.determineQueryType("as13335"))
        assertEquals(WhoisQueryType.ASN, WhoisRdapParser.determineQueryType("15169"))
    }

    @Test
    fun parseRdapResponse_domainSample_extractsKeyFields() {
        val sampleJson = """
        {
          "objectClassName": "domain",
          "ldhName": "example.com",
          "status": ["clientTransferProhibited", "active"],
          "events": [
            { "eventAction": "registration", "eventDate": "1995-08-14T04:00:00Z" },
            { "eventAction": "expiration", "eventDate": "2024-08-13T04:00:00Z" },
            { "eventAction": "last changed", "eventDate": "2023-08-14T07:01:38Z" }
          ],
          "entities": [
            {
              "roles": ["registrar"],
              "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["fn", {}, "text", "RESERVED-Internet Assigned Numbers Authority"]]]
            },
            {
              "roles": ["abuse"],
              "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["email", {}, "text", "abuse@iana.org"]]]
            }
          ],
          "nameservers": [
            { "ldhName": "a.iana-servers.net" },
            { "ldhName": "b.iana-servers.net" }
          ]
        }
        """.trimIndent()

        val summary = WhoisRdapParser.parseRdapResponse(sampleJson, WhoisQueryType.DOMAIN)

        assertEquals("example.com", summary.entityName)
        assertEquals("RESERVED-Internet Assigned Numbers Authority", summary.registrar)
        assertEquals("1995-08-14T04:00:00Z", summary.createdDate)
        assertEquals("2024-08-13T04:00:00Z", summary.expiresDate)
        assertEquals("2023-08-14T07:01:38Z", summary.updatedDate)
        assertEquals("abuse@iana.org", summary.contactEmail)
        assertEquals(2, summary.nameservers.size)
        assertEquals("a.iana-servers.net", summary.nameservers[0])
        assertTrue(summary.statuses.contains("clientTransferProhibited"))
    }

    @Test
    fun parseRdapResponse_ipSample_extractsCidrAndEntity() {
        val sampleJson = """
        {
          "objectClassName": "ip network",
          "handle": "NET-8-8-8-0-1",
          "startAddress": "8.8.8.0",
          "endAddress": "8.8.8.255",
          "ipVersion": "v4",
          "name": "GOGL",
          "status": ["active"]
        }
        """.trimIndent()

        val summary = WhoisRdapParser.parseRdapResponse(sampleJson, WhoisQueryType.IP)

        assertEquals("GOGL", summary.entityName)
        assertEquals("8.8.8.0 - 8.8.8.255 (v4)", summary.cidr)
        assertTrue(summary.statuses.contains("active"))
    }

    @Test
    fun parseRdapResponse_asnSample_extractsAsnNumberAndName() {
        val sampleJson = """
        {
          "objectClassName": "autnum",
          "handle": "AS15169",
          "startAutnum": 15169,
          "endAutnum": 15169,
          "name": "GOOGLE"
        }
        """.trimIndent()

        val summary = WhoisRdapParser.parseRdapResponse(sampleJson, WhoisQueryType.ASN)

        assertEquals("AS15169", summary.asnNumber)
        assertEquals("GOOGLE", summary.asnName)
    }

    @Test
    fun parseRdapResponse_missingFields_handlesGracefully() {
        val emptyJson = "{}"
        val summary = WhoisRdapParser.parseRdapResponse(emptyJson, WhoisQueryType.DOMAIN)
        assertNotNull(summary)
        assertEquals(null, summary.createdDate)
        assertEquals(null, summary.registrar)
        assertTrue(summary.nameservers.isEmpty())
        assertTrue(summary.statuses.isEmpty())
    }

    @Test
    fun parseRdapResponse_malformedJson_returnsErrorSummary() {
        val badJson = "not a valid json {{"
        val summary = WhoisRdapParser.parseRdapResponse(badJson, WhoisQueryType.DOMAIN)
        assertNotNull(summary)
        assertTrue(summary.entityName?.contains("Lỗi") == true)
    }
}
