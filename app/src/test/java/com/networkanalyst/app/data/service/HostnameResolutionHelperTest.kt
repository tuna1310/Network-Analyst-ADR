package com.networkanalyst.app.data.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HostnameResolutionHelperTest {

    private val targetIp = "192.168.1.50"

    @Test
    fun selectBestHostname_mdnsPriority_winsOverAllOthers() {
        val selected = HostnameResolutionHelper.selectBestHostname(
            ip = targetIp,
            mdns = "Living-Room-TV.local",
            netbios = "SAMSUNG-TV",
            llmnr = "smarttv",
            ptr = "host50.lan"
        )
        assertEquals("Living-Room-TV.local", selected)
    }

    @Test
    fun selectBestHostname_netbiosWinsOverLlmnrAndPtr() {
        val selected = HostnameResolutionHelper.selectBestHostname(
            ip = targetIp,
            mdns = null,
            netbios = "DESKTOP-OFFICE",
            llmnr = "desktop-office",
            ptr = "desktop.lan"
        )
        assertEquals("DESKTOP-OFFICE", selected)
    }

    @Test
    fun selectBestHostname_llmnrWinsOverPtr() {
        val selected = HostnameResolutionHelper.selectBestHostname(
            ip = targetIp,
            mdns = null,
            netbios = null,
            llmnr = "ubuntu-server",
            ptr = "server.home.arpa"
        )
        assertEquals("ubuntu-server", selected)
    }

    @Test
    fun selectBestHostname_ptrOnly_returnsPtr() {
        val selected = HostnameResolutionHelper.selectBestHostname(
            ip = targetIp,
            mdns = null,
            netbios = null,
            llmnr = null,
            ptr = "printer.localdomain"
        )
        assertEquals("printer.localdomain", selected)
    }

    @Test
    fun selectBestHostname_candidateMatchingIp_isIgnored() {
        // Nếu resolver trả về chính IP 192.168.1.50 thì bỏ qua và lấy PTR hợp lệ tiếp theo
        val selected = HostnameResolutionHelper.selectBestHostname(
            ip = targetIp,
            mdns = "192.168.1.50",
            netbios = "  192.168.1.50  ",
            llmnr = "synology-nas",
            ptr = "nas.lan"
        )
        assertEquals("synology-nas", selected)
    }

    @Test
    fun selectBestHostname_emptyOrBlankCandidates_areSkipped() {
        val selected = HostnameResolutionHelper.selectBestHostname(
            ip = targetIp,
            mdns = "",
            netbios = "   ",
            llmnr = null,
            ptr = "router.lan"
        )
        assertEquals("router.lan", selected)
    }

    @Test
    fun selectBestHostname_allEmptyOrNull_returnsNull() {
        val selected = HostnameResolutionHelper.selectBestHostname(
            ip = targetIp,
            mdns = null,
            netbios = "",
            llmnr = "  ",
            ptr = "192.168.1.50"
        )
        assertNull(selected)
    }
}
