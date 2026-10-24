package com.networkanalyst.app.data.service

import com.networkanalyst.app.data.model.GeoIpInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

object GeoIpParser {
    fun parseResponse(ip: String, jsonString: String): GeoIpInfo {
        return try {
            val json = JSONObject(jsonString)
            val success = json.optBoolean("success", true)
            if (!success) {
                GeoIpInfo(ip = ip, country = "Không rõ")
            } else {
                val country = json.optString("country", "").ifEmpty { null }
                val countryCode = json.optString("country_code", "").ifEmpty { null }
                val city = json.optString("city", "").ifEmpty { null }
                val connection = json.optJSONObject("connection")
                val isp = connection?.optString("isp", "")?.ifEmpty { null }
                GeoIpInfo(
                    ip = ip,
                    country = country ?: "Không rõ",
                    countryCode = countryCode,
                    city = city,
                    isp = isp
                )
            }
        } catch (_: Exception) {
            GeoIpInfo(ip = ip, country = "Không rõ")
        }
    }
}

@Singleton
class GeoIpService @Inject constructor() {

    // Bộ nhớ đệm tránh truy vấn lặp lại
    private val cache = ConcurrentHashMap<String, GeoIpInfo>()
    private val requestMutex = Mutex()
    private var lastRequestTime = 0L

    companion object {
        const val MAX_SESSION_REQUESTS = 25
        const val REQUEST_INTERVAL_MS = 120L // Giãn cách tránh chạm rate limit
    }

    private var sessionRequestCount = 0

    fun resetSession() {
        sessionRequestCount = 0
    }

    suspend fun lookup(ip: String): GeoIpInfo = withContext(Dispatchers.IO) {
        val cleanIp = ip.trim()
        if (cleanIp.isEmpty() || cleanIp == "* * *") {
            return@withContext GeoIpInfo(ip = cleanIp, country = null)
        }

        // 1. Kiểm tra cache
        cache[cleanIp]?.let { return@withContext it }

        // 2. Không gọi API cho IP Private / CGNAT / Loopback
        if (PortRangeParser.isPrivateIp(cleanIp)) {
            val privateInfo = GeoIpInfo(
                ip = cleanIp,
                country = "Private / LAN",
                countryCode = "LAN",
                isPrivate = true
            )
            cache[cleanIp] = privateInfo
            return@withContext privateInfo
        }

        // 3. Giới hạn số lượng truy vấn mỗi phiên
        if (sessionRequestCount >= MAX_SESSION_REQUESTS) {
            return@withContext GeoIpInfo(ip = cleanIp, country = "Không rõ")
        }

        requestMutex.withLock {
            // Kiểm tra lại cache sau khi acquire lock
            cache[cleanIp]?.let { return@withContext it }

            // Giãn cách request
            val elapsed = System.currentTimeMillis() - lastRequestTime
            if (elapsed < REQUEST_INTERVAL_MS) {
                delay(REQUEST_INTERVAL_MS - elapsed)
            }

            var connection: HttpURLConnection? = null
            try {
                sessionRequestCount++
                val url = URL("https://ipwho.is/$cleanIp")
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                connection.setRequestProperty("User-Agent", "NetworkAnalyst-Android/1.0")

                val responseCode = connection.responseCode
                lastRequestTime = System.currentTimeMillis()

                if (responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = reader.readText()
                    reader.close()

                    val info = GeoIpParser.parseResponse(cleanIp, response)
                    cache[cleanIp] = info
                    return@withContext info
                } else {
                    val fallback = GeoIpInfo(ip = cleanIp, country = "Không rõ")
                    cache[cleanIp] = fallback
                    return@withContext fallback
                }
            } catch (_: Exception) {
                val fallback = GeoIpInfo(ip = cleanIp, country = "Không rõ")
                cache[cleanIp] = fallback
                return@withContext fallback
            } finally {
                connection?.disconnect()
            }
        }
    }
}
