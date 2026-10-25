package com.networkanalyst.app.data.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileInputStream

class OuiLookupTest {

    private lateinit var database: Map<String, String>

    @Before
    fun setUp() {
        val candidates = listOf(
            File("app/src/main/res/raw/oui_database.txt"),
            File("src/main/res/raw/oui_database.txt"),
            File("../app/src/main/res/raw/oui_database.txt")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Không tìm thấy oui_database.txt tại các vị trí: $candidates")

        database = OuiLookupHelper.loadDatabase(FileInputStream(file))
    }

    @Test
    fun normalizeMac_variousFormats_normalizesSuccessfully() {
        assertEquals("AABBCCDDEEFF", OuiLookupHelper.normalizeMac("AA:BB:CC:DD:EE:FF"))
        assertEquals("AABBCCDDEEFF", OuiLookupHelper.normalizeMac("AA-BB-CC-DD-EE-FF"))
        assertEquals("AABBCCDDEEFF", OuiLookupHelper.normalizeMac("aabb.ccdd.eeff"))
        assertEquals("AABBCCDDEEFF", OuiLookupHelper.normalizeMac("aabbccddeeff"))
        assertEquals("AABBCCDDEEFF", OuiLookupHelper.normalizeMac("  aa:bb:cc:dd:ee:ff  "))

        // Chuỗi ngắn hơn 6 ký tự hoặc ký tự không hợp lệ
        assertNull(OuiLookupHelper.normalizeMac("AA:BB"))
        assertNull(OuiLookupHelper.normalizeMac("GG:HH:II:JJ:KK:LL"))
        assertNull(OuiLookupHelper.normalizeMac(null))
        assertNull(OuiLookupHelper.normalizeMac(""))
    }

    @Test
    fun isLocallyAdministered_detectsRandomizedMac() {
        // Bit 1 = 1 (Locally administered / Private MAC)
        // Các ký tự thứ 2 là 2, 6, A, E
        assertTrue(OuiLookupHelper.isLocallyAdministered("020000112233"))
        assertTrue(OuiLookupHelper.isLocallyAdministered("DAA119223344"))
        assertTrue(OuiLookupHelper.isLocallyAdministered("F61234567890"))
        assertTrue(OuiLookupHelper.isLocallyAdministered("3E1234567890"))

        // Bit 1 = 0 (Universally administered / OUI chuẩn IEEE)
        assertFalse(OuiLookupHelper.isLocallyAdministered("001A11AABBCC")) // Google (00)
        assertFalse(OuiLookupHelper.isLocallyAdministered("B827EBAABBCC")) // Raspberry Pi (B8)
        assertFalse(OuiLookupHelper.isLocallyAdministered("3C0754AABBCC")) // Apple (3C)
    }

    @Test
    fun lookupVendor_randomMac_returnsPrivateRandomMacNotice() {
        val vendor1 = OuiLookupHelper.lookupVendor("DA:A1:19:88:99:AA", database)
        assertEquals("Private / Random MAC", vendor1)

        val vendor2 = OuiLookupHelper.lookupVendor("02-12-34-56-78-9A", database)
        assertEquals("Private / Random MAC", vendor2)
    }

    @Test
    fun lookupVendor_knownVendors_resolvesCorrectly() {
        // MA-L (24-bit)
        assertEquals("Google, Inc.", OuiLookupHelper.lookupVendor("00:1A:11:12:34:56", database))
        assertEquals("Raspberry Pi Foundation", OuiLookupHelper.lookupVendor("b8:27:eb:11:22:33", database))
        assertEquals("Apple, Inc.", OuiLookupHelper.lookupVendor("3C-07-54-AA-BB-CC", database))

        // MA-M (28-bit)
        assertEquals("IEEE Registration Authority MA-M", OuiLookupHelper.lookupVendor("00:50:C2:01:23:45", database))

        // MA-S (36-bit)
        assertEquals("IEEE Registration Authority MA-S", OuiLookupHelper.lookupVendor("70:B3:D5:00:01:23", database))
    }

    @Test
    fun lookupVendor_nonExistentMac_returnsNull() {
        // MAC có bit 1 = 0 (phần cứng thật) nhưng prefix chưa đăng ký trong database
        val result = OuiLookupHelper.lookupVendor("00:00:01:00:00:00", database)
        assertNull(result)
    }

    @Test
    fun loadDatabase_resourceIsLoaded_andContainsSubstantialEntries() {
        assertNotNull(database)
        assertTrue("Database OUI phải chứa ít nhất 50 mục", database.size >= 50)
        assertTrue(database.containsKey("001A11"))
        assertTrue(database.containsKey("B827EB"))
    }
}
