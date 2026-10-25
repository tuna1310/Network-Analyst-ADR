package com.networkanalyst.app.data.service

import com.networkanalyst.app.R
import com.networkanalyst.app.data.model.DnsRecordItem
import com.networkanalyst.app.data.model.DnsRecordType
import com.networkanalyst.app.data.model.DnsResolver
import com.networkanalyst.app.ui.components.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.xbill.DNS.AAAARecord
import org.xbill.DNS.ARecord
import org.xbill.DNS.CAARecord
import org.xbill.DNS.CNAMERecord
import org.xbill.DNS.HTTPSRecord
import org.xbill.DNS.MXRecord
import org.xbill.DNS.NSRecord
import org.xbill.DNS.Name
import org.xbill.DNS.PTRRecord
import org.xbill.DNS.Record
import org.xbill.DNS.SOARecord
import org.xbill.DNS.SRVRecord
import org.xbill.DNS.SVCBRecord
import org.xbill.DNS.SimpleResolver
import org.xbill.DNS.TXTRecord
import org.xbill.DNS.Type
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

data class DnsQueryState(
    val isLoading: Boolean = false,
    val domain: String = "",
    val recordType: DnsRecordType = DnsRecordType.A,
    val resolver: DnsResolver = DnsService.PRESET_RESOLVERS[1], // Cloudflare default
    val results: List<DnsRecordItem> = emptyList(),
    val queryTimeMs: Long = 0,
    val error: UiText? = null
)

@Singleton
class DnsService @Inject constructor(
    private val diagnosticsHelper: DiagnosticsHelper
) {
    private val _dnsState = MutableStateFlow(DnsQueryState())
    val dnsState: StateFlow<DnsQueryState> = _dnsState.asStateFlow()

    companion object {
        val PRESET_RESOLVERS = listOf(
            DnsResolver("System (Default)", "SYSTEM", null, "System DNS server from Wi-Fi/Cellular"),
            DnsResolver("Cloudflare DNS", "1.1.1.1", "1.0.0.1", "High speed, privacy focused"),
            DnsResolver("Google Public DNS", "8.8.8.8", "8.8.4.4", "Widely distributed, reliable"),
            DnsResolver("Quad9 DNS", "9.9.9.9", "149.112.112.112", "Blocks malicious domains"),
            DnsResolver("OpenDNS (Cisco)", "208.67.222.222", "208.67.220.220", "Reliable & established"),
            DnsResolver("AdGuard DNS", "94.140.14.14", "94.140.15.15", "Ad-blocking protection"),
            DnsResolver("Custom...", "CUSTOM", null, "Manually enter DNS server IP")
        )

        fun toTypeInt(type: DnsRecordType): Int = when (type) {
            DnsRecordType.A -> Type.A
            DnsRecordType.AAAA -> Type.AAAA
            DnsRecordType.CNAME -> Type.CNAME
            DnsRecordType.MX -> Type.MX
            DnsRecordType.TXT -> Type.TXT
            DnsRecordType.NS -> Type.NS
            DnsRecordType.SOA -> Type.SOA
            DnsRecordType.PTR -> Type.PTR
            DnsRecordType.SRV -> Type.SRV
            DnsRecordType.CAA -> Type.CAA
            DnsRecordType.HTTPS -> Type.HTTPS
            DnsRecordType.SVCB -> Type.SVCB
            DnsRecordType.ALL -> Type.ANY
        }

        internal fun parseDnsRecord(record: Record, expectedType: DnsRecordType, queryDomain: String): DnsRecordItem {
            val ttl = record.ttl
            var priority: Int? = null
            val value: String = when (record) {
                is ARecord -> record.address.hostAddress ?: record.toString()
                is AAAARecord -> record.address.hostAddress ?: record.toString()
                is CNAMERecord -> record.target.toString(true)
                is MXRecord -> {
                    priority = record.priority
                    "${record.priority} ${record.target.toString(true)}"
                }
                is TXTRecord -> record.strings.joinToString(" ")
                is NSRecord -> record.target.toString(true)
                is SOARecord -> {
                    "Host: ${record.host.toString(true)} | Admin: ${record.admin.toString(true)} | Serial: ${record.serial}"
                }
                is PTRRecord -> record.target.toString(true)
                is SRVRecord -> {
                    priority = record.priority
                    "Priority: ${record.priority}, Weight: ${record.weight}, Port: ${record.port}, Target: ${record.target.toString(true)}"
                }
                is CAARecord -> {
                    "${record.flags} ${record.tag} \"${record.value}\""
                }
                is HTTPSRecord -> {
                    priority = record.svcPriority
                    "Priority: ${record.svcPriority}, Target: ${record.targetName.toString(true)} ${record.rdataToString()}"
                }
                is SVCBRecord -> {
                    priority = record.svcPriority
                    "Priority: ${record.svcPriority}, Target: ${record.targetName.toString(true)} ${record.rdataToString()}"
                }
                else -> record.rdataToString()
            }

            return DnsRecordItem(
                queryName = queryDomain,
                type = expectedType,
                value = value,
                ttlSeconds = ttl,
                priority = priority,
                rawText = record.toString()
            )
        }
    }

    fun getSystemDnsServer(): String? {
        return diagnosticsHelper.getNetworkInfoSnapshot().dnsServers.firstOrNull()
    }

    suspend fun query(
        domain: String,
        recordType: DnsRecordType,
        resolver: DnsResolver,
        customResolverIp: String? = null
    ) {
        val cleanDomain = domain.trim().trimEnd('.')
        if (cleanDomain.isEmpty()) {
            _dnsState.value = _dnsState.value.copy(
                isLoading = false,
                error = UiText.StringResource(R.string.err_invalid_domain)
            )
            return
        }

        _dnsState.value = DnsQueryState(
            isLoading = true,
            domain = cleanDomain,
            recordType = recordType,
            resolver = resolver
        )

        val targetResolverIp = when (resolver.primaryIp) {
            "SYSTEM" -> {
                val systemDns = getSystemDnsServer()
                if (systemDns.isNullOrEmpty()) {
                    _dnsState.value = DnsQueryState(
                        isLoading = false,
                        domain = cleanDomain,
                        recordType = recordType,
                        resolver = resolver,
                        error = UiText.StringResource(R.string.err_no_system_dns)
                    )
                    return
                }
                systemDns
            }
            "CUSTOM" -> {
                val custom = customResolverIp?.trim()
                if (custom.isNullOrEmpty()) "1.1.1.1" else custom
            }
            else -> resolver.primaryIp
        }

        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            try {
                val simpleResolver = SimpleResolver(targetResolverIp)
                simpleResolver.timeout = Duration.ofSeconds(4)
                val name = Name.fromString(if (cleanDomain.endsWith(".")) cleanDomain else "$cleanDomain.")

                if (recordType == DnsRecordType.ALL) {
                    val queryTypes = listOf(
                        DnsRecordType.A,
                        DnsRecordType.AAAA,
                        DnsRecordType.CNAME,
                        DnsRecordType.MX,
                        DnsRecordType.TXT,
                        DnsRecordType.NS,
                        DnsRecordType.SOA,
                        DnsRecordType.PTR,
                        DnsRecordType.SRV,
                        DnsRecordType.CAA,
                        DnsRecordType.HTTPS,
                        DnsRecordType.SVCB
                    )

                    val deferreds = queryTypes.map { type ->
                        async(Dispatchers.IO) {
                            try {
                                val lookup = org.xbill.DNS.Lookup(name, toTypeInt(type))
                                lookup.setResolver(simpleResolver)
                                val records = lookup.run()
                                if (lookup.result == org.xbill.DNS.Lookup.SUCCESSFUL && records != null) {
                                    records.map { record -> parseDnsRecord(record, type, cleanDomain) }
                                } else {
                                    emptyList()
                                }
                            } catch (_: Exception) {
                                emptyList()
                            }
                        }
                    }

                    val allResults = deferreds.awaitAll().flatten()
                    val queryTime = System.currentTimeMillis() - startTime

                    if (allResults.isNotEmpty()) {
                        _dnsState.value = DnsQueryState(
                            isLoading = false,
                            domain = cleanDomain,
                            recordType = recordType,
                            resolver = resolver,
                            results = allResults,
                            queryTimeMs = queryTime
                        )
                    } else {
                        _dnsState.value = DnsQueryState(
                            isLoading = false,
                            domain = cleanDomain,
                            recordType = recordType,
                            resolver = resolver,
                            results = emptyList(),
                            queryTimeMs = queryTime,
                            error = UiText.StringResource(R.string.err_dns_all_records_empty, cleanDomain)
                        )
                    }
                    return@withContext
                }

                val typeInt = toTypeInt(recordType)
                val lookup = org.xbill.DNS.Lookup(name, typeInt)
                lookup.setResolver(simpleResolver)
                val rawRecords = lookup.run()

                val queryTime = System.currentTimeMillis() - startTime

                if (lookup.result == org.xbill.DNS.Lookup.SUCCESSFUL && rawRecords != null && rawRecords.isNotEmpty()) {
                    val parsed = rawRecords.map { record ->
                        parseDnsRecord(record, recordType, cleanDomain)
                    }

                    _dnsState.value = DnsQueryState(
                        isLoading = false,
                        domain = cleanDomain,
                        recordType = recordType,
                        resolver = resolver,
                        results = parsed,
                        queryTimeMs = queryTime
                    )
                } else {
                    val errorUiText: UiText = when (lookup.result) {
                        org.xbill.DNS.Lookup.HOST_NOT_FOUND -> UiText.StringResource(R.string.err_dns_nxdomain)
                        org.xbill.DNS.Lookup.TYPE_NOT_FOUND -> UiText.StringResource(R.string.err_dns_type_not_found)
                        org.xbill.DNS.Lookup.UNRECOVERABLE -> UiText.StringResource(R.string.err_dns_unrecoverable)
                        org.xbill.DNS.Lookup.TRY_AGAIN -> UiText.StringResource(R.string.err_dns_timeout)
                        else -> lookup.errorString?.let { UiText.DynamicString(it) } ?: UiText.StringResource(R.string.err_dns_type_not_found)
                    }

                    _dnsState.value = DnsQueryState(
                        isLoading = false,
                        domain = cleanDomain,
                        recordType = recordType,
                        resolver = resolver,
                        results = emptyList(),
                        queryTimeMs = queryTime,
                        error = errorUiText
                    )
                }

            } catch (e: Exception) {
                val queryTime = System.currentTimeMillis() - startTime
                _dnsState.value = DnsQueryState(
                    isLoading = false,
                    domain = cleanDomain,
                    recordType = recordType,
                    resolver = resolver,
                    results = emptyList(),
                    queryTimeMs = queryTime,
                    error = UiText.StringResource(R.string.err_dns_lookup_failed, e.localizedMessage ?: "")
                )
            }
        }
    }
}
