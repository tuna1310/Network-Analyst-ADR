package com.networkanalyst.app.data.service

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkClassifierHelperTest {

    @Test
    fun classifyWifiSecurity_api30Types_parsesCorrectly() {
        assertEquals("Open", NetworkClassifierHelper.classifyWifiSecurity(securityTypeApi30 = 0))
        assertEquals("WEP", NetworkClassifierHelper.classifyWifiSecurity(securityTypeApi30 = 1))
        assertEquals("WPA2-PSK / WPA-Personal", NetworkClassifierHelper.classifyWifiSecurity(securityTypeApi30 = 2))
        assertEquals("WPA2-Enterprise (802.1X)", NetworkClassifierHelper.classifyWifiSecurity(securityTypeApi30 = 3))
        assertEquals("WPA3-Personal (SAE)", NetworkClassifierHelper.classifyWifiSecurity(securityTypeApi30 = 4))
        assertEquals("Enhanced Open (OWE)", NetworkClassifierHelper.classifyWifiSecurity(securityTypeApi30 = 6))
    }

    @Test
    fun classifyWifiSecurity_capabilitiesString_parsesVariousStandards() {
        val wpa3 = NetworkClassifierHelper.classifyWifiSecurity(capabilities = "[WPA3-SAE-CCMP]")
        assertEquals("WPA3-Personal (SAE)", wpa3)

        val mixed = NetworkClassifierHelper.classifyWifiSecurity(capabilities = "[WPA2-PSK-CCMP][RSN-SAE-CCMP]")
        assertEquals("WPA2 / WPA3 Mixed Mode", mixed)

        val wpa2 = NetworkClassifierHelper.classifyWifiSecurity(capabilities = "[WPA2-PSK-CCMP][ESS]")
        assertEquals("WPA2-PSK", wpa2)

        val wep = NetworkClassifierHelper.classifyWifiSecurity(capabilities = "[WEP][ESS]")
        assertEquals("WEP", wep)

        val open = NetworkClassifierHelper.classifyWifiSecurity(capabilities = "[ESS]")
        assertEquals("Open", open)
    }

    @Test
    fun classifyCellularType_mapsGenerationsCorrectly() {
        assertEquals("5G (NR - New Radio)", NetworkClassifierHelper.classifyCellularType(20))
        assertEquals("4G (LTE)", NetworkClassifierHelper.classifyCellularType(13))
        assertEquals("3G (HSPA+ / UMTS)", NetworkClassifierHelper.classifyCellularType(15))
        assertEquals("2G (EDGE / GPRS)", NetworkClassifierHelper.classifyCellularType(2))
        assertEquals("Cellular (Unknown generation)", NetworkClassifierHelper.classifyCellularType(99))
    }

    @Test
    fun classifyTransport_distinguishesEthernetVpnAndWireless() {
        val eth = NetworkClassifierHelper.classifyTransport(isWifi = false, isCellular = false, isEthernet = true, isVpn = false)
        assertEquals("Wired (USB Ethernet)", eth)

        val vpnEth = NetworkClassifierHelper.classifyTransport(isWifi = false, isCellular = false, isEthernet = true, isVpn = true)
        assertEquals("VPN (over USB Ethernet)", vpnEth)

        val wifi = NetworkClassifierHelper.classifyTransport(isWifi = true, isCellular = false, isEthernet = false, isVpn = false)
        assertEquals("Wi-Fi Network", wifi)

        val cellular = NetworkClassifierHelper.classifyTransport(isWifi = false, isCellular = true, isEthernet = false, isVpn = false)
        assertEquals("Mobile Data", cellular)
    }
}
