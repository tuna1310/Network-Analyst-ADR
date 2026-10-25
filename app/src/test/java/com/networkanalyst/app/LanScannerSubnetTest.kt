package com.networkanalyst.app

import com.networkanalyst.app.data.service.LanScannerService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanScannerSubnetTest {

    @Test
    fun testGenerateSubnetIps_prefix24_214_excludesSelf_includesGatewayAndBroadcastBoundary() {
        val ips = LanScannerService.generateSubnetIps("192.168.1.214", 24)
        // 254 host IPs trừ IP máy mình = 253 IPs
        assertEquals(253, ips.size)
        assertTrue("x.x.x.1 phải có trong danh sách", ips.contains("192.168.1.1"))
        assertTrue("x.x.x.254 phải có trong danh sách", ips.contains("192.168.1.254"))
        assertFalse("IP máy mình (192.168.1.214) KHÔNG được có trong danh sách quét", ips.contains("192.168.1.214"))
    }

    @Test
    fun testGenerateSubnetIps_prefix24_100_excludesSelf_includesGatewayAndBroadcastBoundary() {
        val ips = LanScannerService.generateSubnetIps("192.168.1.100", 24)
        assertEquals(253, ips.size)
        assertTrue("x.x.x.1 phải có trong danh sách", ips.contains("192.168.1.1"))
        assertTrue("x.x.x.254 phải có trong danh sách", ips.contains("192.168.1.254"))
        assertFalse("IP máy mình (192.168.1.100) KHÔNG được có trong danh sách quét", ips.contains("192.168.1.100"))
    }

    @Test
    fun testGenerateSubnetIps_prefix24_10_0_0_200_excludesSelf_includesGatewayAndBoundary() {
        val ips = LanScannerService.generateSubnetIps("10.0.0.200", 24)
        assertEquals(253, ips.size)
        assertTrue("10.0.0.1 phải có trong danh sách", ips.contains("10.0.0.1"))
        assertTrue("10.0.0.254 phải có trong danh sách", ips.contains("10.0.0.254"))
        assertFalse("IP máy mình (10.0.0.200) KHÔNG được có trong danh sách quét", ips.contains("10.0.0.200"))
    }

    @Test
    fun testGenerateSubnetIps_prefix23_boundedToMax254() {
        val ips = LanScannerService.generateSubnetIps("192.168.0.50", 23)
        assertTrue("Số IP quét không vượt quá 254", ips.size <= 254)
        assertFalse("IP máy mình không có trong danh sách", ips.contains("192.168.0.50"))
        // Bounded within 192.168.0.1..192.168.1.254
        assertEquals("192.168.0.1", ips.first())
    }

    @Test
    fun testGenerateSubnetIps_prefix16_boundedToMax254AroundDevice() {
        val ips = LanScannerService.generateSubnetIps("172.16.5.9", 16)
        assertTrue("Số IP quét không vượt quá 254", ips.size <= 254)
        assertFalse("IP máy mình không có trong danh sách", ips.contains("172.16.5.9"))
    }

    @Test
    fun testGenerateSubnetIps_prefix30_returnsValidHostWithoutCrash() {
        val ips = LanScannerService.generateSubnetIps("192.168.1.1", 30)
        assertEquals(1, ips.size)
        assertEquals("192.168.1.2", ips[0])
        assertFalse("IP máy mình không có trong danh sách", ips.contains("192.168.1.1"))
    }

    @Test
    fun testGenerateSubnetIps_prefix31And32_returnsEmptyListWithoutCrash() {
        val ips31 = LanScannerService.generateSubnetIps("192.168.1.1", 31)
        assertTrue("/31 trả về danh sách rỗng", ips31.isEmpty())

        val ips32 = LanScannerService.generateSubnetIps("192.168.1.1", 32)
        assertTrue("/32 trả về danh sách rỗng", ips32.isEmpty())
    }
}
