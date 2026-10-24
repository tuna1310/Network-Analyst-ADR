package com.networkanalyst.app.data.service

import com.networkanalyst.app.data.model.WhoisQueryType
import com.networkanalyst.app.data.model.WhoisSummary
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import java.util.regex.Pattern

object WhoisRdapParser {

    private val IPV4_PATTERN = Pattern.compile(
        """^((25[0-5]|(2[0-4]|1\d|[1-9]|)\d)\.?\b){4}$"""
    )

    fun determineQueryType(target: String): WhoisQueryType {
        val clean = target.trim()
        if (clean.uppercase().startsWith("AS") && clean.substring(2).all { it.isDigit() }) {
            return WhoisQueryType.ASN
        }
        if (clean.isNotEmpty() && clean.all { it.isDigit() }) {
            return WhoisQueryType.ASN
        }
        if (IPV4_PATTERN.matcher(clean).matches() || clean.contains(":")) {
            return WhoisQueryType.IP
        }
        return WhoisQueryType.DOMAIN
    }

    /**
     * Trích xuất thông tin tóm tắt từ cấu trúc JSON chuẩn của RDAP (RFC 7483 / RFC 9083).
     */
    fun parseRdapResponse(jsonString: String, queryType: WhoisQueryType): WhoisSummary {
        return try {
            val root = JSONObject(jsonString)

            var createdDate: String? = null
            var expiresDate: String? = null
            var updatedDate: String? = null

            val events = root.optJSONArray("events")
            if (events != null) {
                for (i in 0 until events.length()) {
                    val ev = events.optJSONObject(i) ?: continue
                    val action = ev.optString("eventAction", "").lowercase()
                    val date = ev.optString("eventDate", "").ifEmpty { null }
                    when (action) {
                        "registration" -> createdDate = date
                        "expiration" -> expiresDate = date
                        "last changed", "last update" -> updatedDate = date
                    }
                }
            }

            // Statuses
            val statuses = mutableListOf<String>()
            val statusArr = root.optJSONArray("status")
            if (statusArr != null) {
                for (i in 0 until statusArr.length()) {
                    val s = statusArr.optString(i)
                    if (s.isNotBlank()) statuses.add(s)
                }
            }

            // Nameservers
            val nameservers = mutableListOf<String>()
            val nsArr = root.optJSONArray("nameservers")
            if (nsArr != null) {
                for (i in 0 until nsArr.length()) {
                    val nsObj = nsArr.optJSONObject(i)
                    val nsName = nsObj?.optString("ldhName", "") ?: ""
                    if (nsName.isNotBlank()) nameservers.add(nsName)
                }
            }

            // Entities: Registrar, Registrant, Contact Email
            var registrarName: String? = null
            var entityName: String? = null
            var contactEmail: String? = null

            val entities = root.optJSONArray("entities")
            if (entities != null) {
                for (i in 0 until entities.length()) {
                    val ent = entities.optJSONObject(i) ?: continue
                    val roles = ent.optJSONArray("roles")
                    val isRegistrar = hasRole(roles, "registrar")
                    val isRegistrant = hasRole(roles, "registrant")
                    val isAbuse = hasRole(roles, "abuse")

                    val vcard = ent.optJSONArray("vcardArray")
                    val fn = extractVCardProperty(vcard, "fn")
                    val email = extractVCardProperty(vcard, "email")

                    if (isRegistrar && registrarName == null) {
                        registrarName = fn ?: ent.optString("handle").ifEmpty { null }
                    }
                    if (isRegistrant && entityName == null) {
                        entityName = fn ?: ent.optString("handle").ifEmpty { null }
                    }
                    if (email != null && (contactEmail == null || isAbuse)) {
                        contactEmail = email
                    }
                }
            }

            // CIDR & ASN
            var cidr: String? = null
            var asnNumber: String? = null
            var asnName: String? = null

            when (queryType) {
                WhoisQueryType.IP -> {
                    val start = root.optString("startAddress", "")
                    val end = root.optString("endAddress", "")
                    val ipVersion = root.optString("ipVersion", "v4")
                    cidr = if (start.isNotBlank() && end.isNotBlank()) "$start - $end ($ipVersion)" else null
                    if (entityName == null) {
                        entityName = root.optString("name").ifEmpty { null }
                    }
                }
                WhoisQueryType.ASN -> {
                    val startAut = root.optLong("startAutnum", -1L)
                    val endAut = root.optLong("endAutnum", -1L)
                    asnNumber = if (startAut > 0) "AS$startAut" else null
                    asnName = root.optString("name").ifEmpty { null }
                }
                WhoisQueryType.DOMAIN -> {
                    if (entityName == null) {
                        entityName = root.optString("ldhName").ifEmpty { null }
                    }
                }
            }

            WhoisSummary(
                entityName = entityName,
                registrar = registrarName,
                createdDate = createdDate,
                expiresDate = expiresDate,
                updatedDate = updatedDate,
                statuses = statuses,
                cidr = cidr,
                nameservers = nameservers,
                contactEmail = contactEmail,
                asnNumber = asnNumber,
                asnName = asnName
            )
        } catch (_: Exception) {
            WhoisSummary(entityName = "Lỗi phân tích cú pháp RDAP JSON")
        }
    }

    private fun hasRole(roles: JSONArray?, targetRole: String): Boolean {
        if (roles == null) return false
        for (i in 0 until roles.length()) {
            if (roles.optString(i).equals(targetRole, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun extractVCardProperty(vcard: JSONArray?, propertyName: String): String? {
        if (vcard == null || vcard.length() < 2) return null
        val props = vcard.optJSONArray(1) ?: return null
        for (i in 0 until props.length()) {
            val item = props.optJSONArray(i) ?: continue
            if (item.length() >= 4 && item.optString(0).equals(propertyName, ignoreCase = true)) {
                return item.optString(3).ifEmpty { null }
            }
        }
        return null
    }

    /**
     * Tìm máy chủ referral từ phản hồi TCP 43 (whois.iana.org hoặc tương đương).
     * Hỗ trợ các mẫu: "refer: whois.verisign-grs.com", "Whois Server: whois.markmonitor.com", "whois: whois.nic.uk".
     */
    fun parseWhoisReferral(whoisText: String): String? {
        val lines = whoisText.lines()
        val pattern = Pattern.compile(
            """(?:refer|whois\s+server|registrar\s+whois\s+server|whois):\s*([a-zA-Z0-9.-]+)""",
            Pattern.CASE_INSENSITIVE
        )

        for (line in lines) {
            val clean = line.trim()
            val matcher = pattern.matcher(clean)
            if (matcher.find()) {
                val host = matcher.group(1)?.trim()
                if (!host.isNullOrEmpty() && host != "whois.iana.org") {
                    return host
                }
            }
        }
        return null
    }

    /**
     * Xác thực máy chủ referral chống tấn công SSRF (Server-Side Request Forgery).
     * Tuyệt đối không cho phép chuyển tiếp tới IP nội bộ, loopback, CGNAT hoặc private IP.
     */
    fun isValidReferralHost(host: String): Boolean {
        val clean = host.trim().lowercase()
        if (clean.isEmpty()) return false
        if (clean == "localhost" || clean == "127.0.0.1" || clean == "::1") return false
        if (PortRangeParser.isPrivateIp(clean)) return false

        // Kiểm tra xem host có phân giải ra IP private hay không
        return try {
            val inet = InetAddress.getByName(clean)
            val ip = inet.hostAddress ?: clean
            !PortRangeParser.isPrivateIp(ip)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Trích xuất thông tin cơ bản từ văn bản Whois TCP 43 thuần (Text).
     */
    fun parseWhoisText(whoisText: String): WhoisSummary {
        val lines = whoisText.lines()
        var entityName: String? = null
        var registrar: String? = null
        var createdDate: String? = null
        var expiresDate: String? = null
        var updatedDate: String? = null
        val statuses = mutableListOf<String>()
        val nameservers = mutableListOf<String>()
        var contactEmail: String? = null
        var cidr: String? = null

        for (line in lines) {
            val clean = line.trim()
            val lower = clean.lowercase()

            when {
                lower.startsWith("domain name:") -> {
                    if (entityName == null) entityName = clean.substringAfter(":").trim()
                }
                lower.startsWith("orgname:") || lower.startsWith("registrant organization:") -> {
                    entityName = clean.substringAfter(":").trim()
                }
                lower.startsWith("registrar:") && registrar == null -> {
                    registrar = clean.substringAfter(":").trim()
                }
                (lower.startsWith("creation date:") || lower.startsWith("created:")) && createdDate == null -> {
                    createdDate = clean.substringAfter(":").trim()
                }
                (lower.startsWith("registry expiry date:") || lower.startsWith("registrar registration expiration date:") || lower.startsWith("expires:")) && expiresDate == null -> {
                    expiresDate = clean.substringAfter(":").trim()
                }
                (lower.startsWith("updated date:") || lower.startsWith("last updated:")) && updatedDate == null -> {
                    updatedDate = clean.substringAfter(":").trim()
                }
                lower.startsWith("domain status:") -> {
                    val st = clean.substringAfter(":").trim().split(" ").firstOrNull()?.trim() ?: ""
                    if (st.isNotBlank() && !statuses.contains(st)) statuses.add(st)
                }
                lower.startsWith("name server:") || lower.startsWith("nserver:") -> {
                    val ns = clean.substringAfter(":").trim().split(" ").firstOrNull()?.trim() ?: ""
                    if (ns.isNotBlank() && !nameservers.contains(ns)) nameservers.add(ns)
                }
                (lower.startsWith("registrar abuse contact email:") || lower.startsWith("contact email:")) && contactEmail == null -> {
                    contactEmail = clean.substringAfter(":").trim()
                }
                lower.startsWith("cidr:") || lower.startsWith("netrange:") -> {
                    cidr = clean.substringAfter(":").trim()
                }
            }
        }

        return WhoisSummary(
            entityName = entityName,
            registrar = registrar,
            createdDate = createdDate,
            expiresDate = expiresDate,
            updatedDate = updatedDate,
            statuses = statuses,
            cidr = cidr,
            nameservers = nameservers,
            contactEmail = contactEmail
        )
    }
}
