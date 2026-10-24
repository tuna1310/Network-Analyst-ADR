package com.networkanalyst.app.data.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhoisReferralParserTest {

    @Test
    fun parseWhoisReferral_ianaFormat_extractsReferralHost() {
        val ianaResponse = """
        % IANA WHOIS server
        % for more information on IANA, visit http://www.iana.org
        % This query returned 1 object

        refer:        whois.verisign-grs.com

        domain:       COM
        organisation: VeriSign Global Registry Services
        """.trimIndent()

        val referral = WhoisRdapParser.parseWhoisReferral(ianaResponse)
        assertEquals("whois.verisign-grs.com", referral)
    }

    @Test
    fun parseWhoisReferral_verisignFormat_extractsRegistrarServer() {
        val response = """
        Domain Name: GOOGLE.COM
        Registry Domain ID: 2138514_DOMAIN_COM-VRSN
        Registrar WHOIS Server: whois.markmonitor.com
        Registrar URL: http://www.markmonitor.com
        Updated Date: 2019-09-09T15:39:04Z
        """.trimIndent()

        val referral = WhoisRdapParser.parseWhoisReferral(response)
        assertEquals("whois.markmonitor.com", referral)
    }

    @Test
    fun parseWhoisReferral_nicFormat_extractsWhoisHost() {
        val response = """
        Domain name:
            bbc.co.uk

        Data validation:
            Nominet was able to match the registrant's name and address against a public data source

        Registrar:
            British Broadcasting Corporation [Tag = BBC]

        WHOIS: whois.nic.uk
        """.trimIndent()

        val referral = WhoisRdapParser.parseWhoisReferral(response)
        assertEquals("whois.nic.uk", referral)
    }

    @Test
    fun parseWhoisReferral_noReferralOrSelf_returnsNull() {
        val response = """
        refer: whois.iana.org
        domain: EXAMPLE
        """.trimIndent()
        assertNull(WhoisRdapParser.parseWhoisReferral(response))

        val noReferral = "Domain not found."
        assertNull(WhoisRdapParser.parseWhoisReferral(noReferral))
    }

    @Test
    fun isValidReferralHost_ssrfPrevention_rejectsPrivateAndLoopback() {
        // Valid hosts
        assertTrue(WhoisRdapParser.isValidReferralHost("whois.verisign-grs.com"))
        assertTrue(WhoisRdapParser.isValidReferralHost("whois.markmonitor.com"))

        // Reject localhost & loopback
        assertFalse(WhoisRdapParser.isValidReferralHost("localhost"))
        assertFalse(WhoisRdapParser.isValidReferralHost("127.0.0.1"))
        assertFalse(WhoisRdapParser.isValidReferralHost("::1"))

        // Reject RFC 1918 Private IPs
        assertFalse(WhoisRdapParser.isValidReferralHost("192.168.1.1"))
        assertFalse(WhoisRdapParser.isValidReferralHost("10.0.0.1"))
        assertFalse(WhoisRdapParser.isValidReferralHost("172.16.0.1"))

        // Reject Link-local & CGNAT
        assertFalse(WhoisRdapParser.isValidReferralHost("169.254.1.1"))
        assertFalse(WhoisRdapParser.isValidReferralHost("100.64.0.1"))
        assertFalse(WhoisRdapParser.isValidReferralHost(""))
    }

    @Test
    fun parseWhoisText_standardText_extractsSummary() {
        val sample = """
        Domain Name: google.com
        Registry Domain ID: 2138514_DOMAIN_COM-VRSN
        Registrar: MarkMonitor Inc.
        Creation Date: 1997-09-15T04:00:00Z
        Registry Expiry Date: 2028-09-14T04:00:00Z
        Updated Date: 2019-09-09T15:39:04Z
        Domain Status: clientDeleteProhibited
        Domain Status: clientTransferProhibited
        Name Server: NS1.GOOGLE.COM
        Name Server: NS2.GOOGLE.COM
        Registrar Abuse Contact Email: abusecomplaints@markmonitor.com
        """.trimIndent()

        val summary = WhoisRdapParser.parseWhoisText(sample)
        assertNotNull(summary)
        assertEquals("google.com", summary.entityName)
        assertEquals("MarkMonitor Inc.", summary.registrar)
        assertEquals("1997-09-15T04:00:00Z", summary.createdDate)
        assertEquals("2028-09-14T04:00:00Z", summary.expiresDate)
        assertEquals("2019-09-09T15:39:04Z", summary.updatedDate)
        assertEquals("abusecomplaints@markmonitor.com", summary.contactEmail)
        assertEquals(2, summary.statuses.size)
        assertEquals(2, summary.nameservers.size)
    }
}
