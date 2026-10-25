package com.networkanalyst.app.data.service

import com.networkanalyst.app.data.model.WhoisQueryType
import com.networkanalyst.app.data.model.WhoisResult
import com.networkanalyst.app.data.model.WhoisSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhoisRdapService @Inject constructor() {

    data class CachedBootstrap(val content: String, val timestamp: Long)

    // Bộ nhớ đệm các file bootstrap IANA với thời hạn 24 giờ để tránh tải lại liên tục
    private val bootstrapCache = ConcurrentHashMap<String, CachedBootstrap>()

    companion object {
        const val IANA_DNS_BOOTSTRAP = "https://data.iana.org/rdap/dns.json"
        const val IANA_IPV4_BOOTSTRAP = "https://data.iana.org/rdap/ipv4.json"
        const val IANA_IPV6_BOOTSTRAP = "https://data.iana.org/rdap/ipv6.json"
        const val IANA_ASN_BOOTSTRAP = "https://data.iana.org/rdap/asn.json"

        // Thời hạn lưu bộ đệm Bootstrap: 24 giờ
        const val BOOTSTRAP_CACHE_TTL_MS = 24 * 60 * 60 * 1000L

        const val RDAP_ORG_BASE = "https://rdap.org"
        const val DEFAULT_WHOIS_SERVER = "whois.iana.org"
        const val MAX_WHOIS_REFERRALS = 3
        const val MAX_WHOIS_RESPONSE_BYTES = 65536 // 64 KB
        const val SOCKET_TIMEOUT_MS = 5000
    }

    suspend fun query(target: String): WhoisResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val cleanTarget = target.trim()
        val queryType = WhoisRdapParser.determineQueryType(cleanTarget)

        // 1. Thử truy vấn qua RDAP IANA Bootstrap
        val rdapIanaResult = tryQueryRdapIana(cleanTarget, queryType)
        if (rdapIanaResult != null) {
            val elapsed = System.currentTimeMillis() - startTime
            val summary = WhoisRdapParser.parseRdapResponse(rdapIanaResult, queryType)
            return@withContext WhoisResult(
                target = cleanTarget,
                queryType = queryType,
                source = WhoisSource.RDAP_IANA,
                summary = summary,
                rawContent = rdapIanaResult,
                queryTimeMs = elapsed
            )
        }

        // 2. Thử truy vấn dự phòng qua rdap.org
        val rdapOrgResult = tryQueryRdapOrg(cleanTarget, queryType)
        if (rdapOrgResult != null) {
            val elapsed = System.currentTimeMillis() - startTime
            val summary = WhoisRdapParser.parseRdapResponse(rdapOrgResult, queryType)
            return@withContext WhoisResult(
                target = cleanTarget,
                queryType = queryType,
                source = WhoisSource.RDAP_ORG_FALLBACK,
                summary = summary,
                rawContent = rdapOrgResult,
                queryTimeMs = elapsed
            )
        }

        // 3. Fallback cuối cùng qua Whois TCP 43 với IANA Referral
        val whoisTcpResult = queryWhoisTcpWithReferral(cleanTarget)
        val elapsed = System.currentTimeMillis() - startTime
        val summary = WhoisRdapParser.parseWhoisText(whoisTcpResult)
        return@withContext WhoisResult(
            target = cleanTarget,
            queryType = queryType,
            source = WhoisSource.WHOIS_PORT_43,
            summary = summary,
            rawContent = whoisTcpResult,
            queryTimeMs = elapsed
        )
    }

    /**
     * Tra cứu RDAP từ máy chủ authoritative xác định qua IANA Bootstrap Registry.
     */
    private suspend fun tryQueryRdapIana(target: String, queryType: WhoisQueryType): String? {
        val baseUrl = getAuthoritativeRdapUrl(target, queryType) ?: return null
        val fullUrl = buildRdapTargetUrl(baseUrl, target, queryType)
        return fetchHttpsUrl(fullUrl)
    }

    /**
     * Tra cứu qua cổng dự phòng rdap.org.
     */
    private suspend fun tryQueryRdapOrg(target: String, queryType: WhoisQueryType): String? {
        val path = when (queryType) {
            WhoisQueryType.DOMAIN -> "domain"
            WhoisQueryType.IP -> "ip"
            WhoisQueryType.ASN -> "autnum"
        }
        val clean = if (queryType == WhoisQueryType.ASN) {
            target.uppercase().removePrefix("AS")
        } else target

        val fullUrl = "$RDAP_ORG_BASE/$path/$clean"
        return fetchHttpsUrl(fullUrl)
    }

    private suspend fun getAuthoritativeRdapUrl(target: String, queryType: WhoisQueryType): String? {
        return try {
            val bootstrapUrl = when (queryType) {
                WhoisQueryType.DOMAIN -> IANA_DNS_BOOTSTRAP
                WhoisQueryType.IP -> if (target.contains(":")) IANA_IPV6_BOOTSTRAP else IANA_IPV4_BOOTSTRAP
                WhoisQueryType.ASN -> IANA_ASN_BOOTSTRAP
            }

            val now = System.currentTimeMillis()
            val cached = bootstrapCache[bootstrapUrl]
            val bootstrapJson = if (cached != null && (now - cached.timestamp) < BOOTSTRAP_CACHE_TTL_MS) {
                cached.content
            } else {
                val fetched = fetchHttpsUrl(bootstrapUrl) ?: return null
                bootstrapCache[bootstrapUrl] = CachedBootstrap(fetched, now)
                fetched
            }

            WhoisRdapParser.findBootstrapUrl(bootstrapJson, target, queryType)
        } catch (_: Exception) {
            null
        }
    }

    private fun buildRdapTargetUrl(baseUrl: String, target: String, queryType: WhoisQueryType): String {
        val base = baseUrl.trimEnd('/')
        return when (queryType) {
            WhoisQueryType.DOMAIN -> "$base/domain/$target"
            WhoisQueryType.IP -> "$base/ip/$target"
            WhoisQueryType.ASN -> {
                val num = target.uppercase().removePrefix("AS")
                "$base/autnum/$num"
            }
        }
    }

    private suspend fun fetchHttpsUrl(urlString: String): String? = withContext(Dispatchers.IO) {
        var currentUrl = urlString
        var redirectCount = 0

        while (redirectCount < 5) {
            var conn: HttpURLConnection? = null
            try {
                val url = URL(currentUrl)
                conn = url.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                conn.setRequestProperty("Accept", "application/rdap+json, application/json")
                conn.setRequestProperty("User-Agent", "NetworkAnalyst-Android/1.0")

                val status = conn.responseCode
                if (status in 300..399) {
                    val location = conn.getHeaderField("Location") ?: return@withContext null
                    currentUrl = location
                    redirectCount++
                    continue
                }

                if (status == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val body = reader.readText()
                    reader.close()
                    return@withContext body
                }
                return@withContext null
            } catch (_: Exception) {
                return@withContext null
            } finally {
                conn?.disconnect()
            }
        }
        null
    }

    /**
     * Truy vấn Whois qua TCP Port 43:
     * 1. Phân giải và kiểm tra máy chủ an toàn (loại trừ private/loopback/link-local/CGNAT)
     * 2. Kết nối trực tiếp tới IP đã phân giải (ngăn chặn tấn công DNS Rebinding)
     * 3. Tìm referral host (refer: <host>)
     * 4. Lặp lại tối đa 3 lần chuyển tiếp an toàn
     */
    private suspend fun queryWhoisTcpWithReferral(target: String): String = withContext(Dispatchers.IO) {
        var currentServer = DEFAULT_WHOIS_SERVER
        var currentAddress = WhoisRdapParser.resolveSafeReferralAddress(currentServer)
        if (currentAddress == null) {
            return@withContext "Cannot resolve primary Whois server $DEFAULT_WHOIS_SERVER."
        }

        var currentOutput = ""
        var hopCount = 0

        while (hopCount < MAX_WHOIS_REFERRALS) {
            val addr = currentAddress ?: break
            hopCount++
            val rawResponse = querySingleWhoisServer(addr, currentServer, target)
            currentOutput = rawResponse

            val referral = WhoisRdapParser.parseWhoisReferral(rawResponse)
            if (referral != null && referral != currentServer) {
                val nextAddress = WhoisRdapParser.resolveSafeReferralAddress(referral)
                if (nextAddress != null) {
                    currentServer = referral
                    currentAddress = nextAddress
                } else {
                    break
                }
            } else {
                break
            }
        }

        currentOutput.ifEmpty { "No response received from Whois server." }
    }

    private fun querySingleWhoisServer(address: java.net.InetAddress, serverName: String, target: String): String {
        var socket: Socket? = null
        return try {
            socket = Socket()
            socket.soTimeout = SOCKET_TIMEOUT_MS
            // Connect to verified safe address
            socket.connect(InetSocketAddress(address, 43), SOCKET_TIMEOUT_MS)

            val writer = OutputStreamWriter(socket.getOutputStream(), "UTF-8")
            writer.write("$target\r\n")
            writer.flush()

            val inputStream = socket.getInputStream()
            val buffer = ByteArray(4096)
            val byteStream = ByteArrayOutputStream()
            var totalRead = 0
            var bytesRead: Int

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                if (totalRead + bytesRead > MAX_WHOIS_RESPONSE_BYTES) {
                    val allowed = MAX_WHOIS_RESPONSE_BYTES - totalRead
                    if (allowed > 0) {
                        byteStream.write(buffer, 0, allowed)
                    }
                    break
                }
                byteStream.write(buffer, 0, bytesRead)
                totalRead += bytesRead
            }

            byteStream.toString("UTF-8")
        } catch (e: Exception) {
            "Failed to connect to Whois server $serverName (${address.hostAddress}): ${e.localizedMessage}"
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }
}
