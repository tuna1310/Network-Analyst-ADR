package com.networkanalyst.app.data.service

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

/**
 * Helper tra cứu nhà sản xuất từ địa chỉ MAC (OUI Lookup).
 *
 * LƯU Ý VỀ NGUỒN DỮ LIỆU:
 * CSDL đi kèm hiện tại trong `app/src/main/res/raw/oui_database.txt` là:
 * "DỮ LIỆU MẪU, 207 hãng" (tập hợp các tiền tố OUI phổ biến như Cisco, Apple, Espressif, Intel, Samsung, v.v.).
 * Chưa sinh từ file `oui.csv` đầy đủ của IEEE. Để có CSDL toàn diện hơn 35.000+ hãng, người dùng cần
 * tự tải file `oui.csv` đặt vào `scripts/data/oui.csv` và chạy script `scripts/generate_oui_data.py`.
 */
object OuiLookupHelper {

    private val cachedDatabase = ConcurrentHashMap<String, String>()
    private var isLoaded = false

    /**
     * Chuẩn hóa địa chỉ MAC:
     * - Hỗ trợ các định dạng phân cách phổ biến: "AA:BB:CC:DD:EE:FF", "AA-BB-CC-DD-EE-FF", "aabb.ccdd.eeff".
     * - Hỗ trợ chuỗi viết liền không phân cách: "AABBCCDDEEFF".
     * - Loại bỏ khoảng trắng và chuyển toàn bộ thành chữ in hoa.
     */
    fun normalizeMac(rawMac: String?): String? {
        if (rawMac.isNullOrBlank()) return null
        val clean = rawMac.replace(":", "")
            .replace("-", "")
            .replace(".", "")
            .replace(" ", "")
            .trim()
            .uppercase()

        if (clean.length < 6) return null
        if (!clean.all { it in '0'..'9' || it in 'A'..'F' }) return null
        return clean
    }

    /**
     * Kiểm tra bit "Locally Administered" (bit thứ 2 của byte đầu tiên).
     *
     * Theo tiêu chuẩn IEEE 802:
     * - Byte đầu tiên dạng nhị phân: b7 b6 b5 b4 b3 b2 b1 b0
     * - Bit b1 (bit thứ 2 từ phải sang) = 1: Địa chỉ MAC cục bộ / MAC ngẫu nhiên (Locally Administered / Randomized).
     *   Được Android 10+, iOS 14+, Windows 10/11 sử dụng cho tính năng "MAC ngẫu nhiên để bảo vệ quyền riêng tư".
     * - Bit b1 = 0: Địa chỉ MAC toàn cầu do IEEE cấp phép cho nhà sản xuất (Universally Administered).
     *
     * Tương ứng với ký tự hex thứ 2 là một trong các giá trị: 2, 6, A, E.
     */
    fun isLocallyAdministered(normalizedMac: String): Boolean {
        if (normalizedMac.length < 2) return false
        val firstOctetHex = normalizedMac.take(2)
        val firstByte = firstOctetHex.toIntOrNull(16) ?: return false
        return (firstByte and 0x02) != 0
    }

    /**
     * Nạp dữ liệu OUI từ InputStream của tệp resource (app/src/main/res/raw/oui_database.txt).
     */
    fun loadDatabase(inputStream: InputStream): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        reader.useLines { lines ->
            lines.forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && trimmed.contains("=")) {
                    val parts = trimmed.split("=", limit = 2)
                    if (parts.size == 2) {
                        val prefix = parts[0].trim().uppercase()
                        val vendor = parts[1].trim()
                        if (prefix.isNotEmpty() && vendor.isNotEmpty()) {
                            map[prefix] = vendor
                        }
                    }
                }
            }
        }
        return map
    }

    /**
     * Khởi tạo bộ đệm tĩnh dùng chung nếu chưa được nạp.
     */
    @Synchronized
    fun initializeIfNeeded(inputStream: InputStream) {
        if (!isLoaded) {
            val loaded = loadDatabase(inputStream)
            cachedDatabase.putAll(loaded)
            isLoaded = true
        }
    }

    /**
     * Tra cứu nhà sản xuất thiết bị từ địa chỉ MAC:
     * 1. Chuẩn hóa MAC address.
     * 2. Nếu là MAC ngẫu nhiên (Locally Administered), lập tức trả về "Private / Random MAC".
     * 3. Khớp tiền tố dài nhất trước:
     *    - MA-S (36-bit): 9 ký tự hex
     *    - MA-M (28-bit): 7 ký tự hex
     *    - MA-L (24-bit): 6 ký tự hex
     * 4. Trả về tên nhà sản xuất hoặc null nếu không tìm thấy.
     */
    fun lookupVendor(rawMac: String?, database: Map<String, String> = cachedDatabase): String? {
        val clean = normalizeMac(rawMac) ?: return null

        if (isLocallyAdministered(clean)) {
            return "Private / Random MAC"
        }

        // 1. Thử MA-S (9 ký tự hex)
        if (clean.length >= 9) {
            val prefix9 = clean.take(9)
            val vendor = database[prefix9]
            if (vendor != null) return vendor
        }

        // 2. Thử MA-M (7 ký tự hex)
        if (clean.length >= 7) {
            val prefix7 = clean.take(7)
            val vendor = database[prefix7]
            if (vendor != null) return vendor
        }

        // 3. Thử MA-L (6 ký tự hex)
        val prefix6 = clean.take(6)
        return database[prefix6]
    }
}
