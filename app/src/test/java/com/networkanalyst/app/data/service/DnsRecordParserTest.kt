package com.networkanalyst.app.data.service

import com.networkanalyst.app.data.model.DnsRecordType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.xbill.DNS.DClass
import org.xbill.DNS.Name
import org.xbill.DNS.Record
import org.xbill.DNS.Type

class DnsRecordParserTest {

    @Test
    fun parseDnsRecord_caaRecord_decodesCorrectly() {
        val name = Name.fromString("example.com.")
        val caaRecord = Record.fromString(
            name,
            Type.CAA,
            DClass.IN,
            3600,
            "0 issue \"letsencrypt.org\"",
            Name.root
        )

        assertNotNull(caaRecord)
        val item = DnsService.parseDnsRecord(caaRecord, DnsRecordType.CAA, "example.com")

        assertEquals(3600L, item.ttlSeconds)
        assertEquals(DnsRecordType.CAA, item.type)
        assertTrue(item.value.contains("letsencrypt.org"))
        assertTrue(item.value.contains("issue"))
    }

    @Test
    fun parseDnsRecord_httpsRecord_decodesPriorityAndTarget() {
        val name = Name.fromString("example.com.")
        // HTTPS Record: SvcPriority = 1, TargetName = ., alpn=h3,h2
        val httpsRecord = Record.fromString(
            name,
            Type.HTTPS,
            DClass.IN,
            300,
            "1 . alpn=h3,h2",
            Name.root
        )

        assertNotNull(httpsRecord)
        val item = DnsService.parseDnsRecord(httpsRecord, DnsRecordType.HTTPS, "example.com")

        assertEquals(300L, item.ttlSeconds)
        assertEquals(DnsRecordType.HTTPS, item.type)
        assertEquals(1, item.priority)
        assertTrue(item.value.contains("Priority: 1"))
        assertTrue(item.value.contains("alpn"))
    }

    @Test
    fun parseDnsRecord_svcbRecord_decodesPriorityAndTarget() {
        val name = Name.fromString("_dns.example.com.")
        val svcbRecord = Record.fromString(
            name,
            Type.SVCB,
            DClass.IN,
            600,
            "16 dns.example.com. alpn=dot",
            Name.root
        )

        assertNotNull(svcbRecord)
        val item = DnsService.parseDnsRecord(svcbRecord, DnsRecordType.SVCB, "_dns.example.com")

        assertEquals(600L, item.ttlSeconds)
        assertEquals(DnsRecordType.SVCB, item.type)
        assertEquals(16, item.priority)
        assertTrue(item.value.contains("Priority: 16"))
        assertTrue(item.value.contains("dns.example.com"))
    }

    @Test
    fun parseDnsRecord_aRecord_decodesIpAndTtl() {
        val name = Name.fromString("example.com.")
        val aRecord = Record.fromString(
            name,
            Type.A,
            DClass.IN,
            300,
            "93.184.216.34",
            Name.root
        )

        assertNotNull(aRecord)
        val item = DnsService.parseDnsRecord(aRecord, DnsRecordType.A, "example.com")

        assertEquals(300L, item.ttlSeconds)
        assertEquals(DnsRecordType.A, item.type)
        assertEquals("93.184.216.34", item.value)
    }

    @Test
    fun parseDnsRecord_mxRecord_decodesPriorityAndTarget() {
        val name = Name.fromString("example.com.")
        val mxRecord = Record.fromString(
            name,
            Type.MX,
            DClass.IN,
            1800,
            "10 mail.example.com.",
            Name.root
        )

        assertNotNull(mxRecord)
        val item = DnsService.parseDnsRecord(mxRecord, DnsRecordType.MX, "example.com")

        assertEquals(1800L, item.ttlSeconds)
        assertEquals(DnsRecordType.MX, item.type)
        assertEquals(10, item.priority)
        assertTrue(item.value.contains("mail.example.com"))
    }
}
