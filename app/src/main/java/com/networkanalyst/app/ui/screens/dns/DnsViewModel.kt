package com.networkanalyst.app.ui.screens.dns

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.networkanalyst.app.data.local.HistoryDao
import com.networkanalyst.app.data.local.entity.HistoryEntity
import com.networkanalyst.app.data.model.DnsRecordType
import com.networkanalyst.app.data.model.DnsResolver
import com.networkanalyst.app.data.model.ToolType
import com.networkanalyst.app.data.service.AdService
import com.networkanalyst.app.data.service.DnsQueryState
import com.networkanalyst.app.data.service.DnsService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DnsViewModel @Inject constructor(
    private val dnsService: DnsService,
    val adService: AdService,
    private val historyDao: HistoryDao
) : ViewModel() {

    val dnsState: StateFlow<DnsQueryState> = dnsService.dnsState

    private val _domainInput = MutableStateFlow("google.com")
    val domainInput: StateFlow<String> = _domainInput.asStateFlow()

    private val _selectedRecordType = MutableStateFlow(DnsRecordType.A)
    val selectedRecordType: StateFlow<DnsRecordType> = _selectedRecordType.asStateFlow()

    private val _selectedResolver = MutableStateFlow(DnsService.PRESET_RESOLVERS[1]) // Cloudflare
    val selectedResolver: StateFlow<DnsResolver> = _selectedResolver.asStateFlow()

    private val _customResolverIp = MutableStateFlow("")
    val customResolverIp: StateFlow<String> = _customResolverIp.asStateFlow()

    fun setDomain(domain: String) {
        _domainInput.value = domain
    }

    fun setRecordType(type: DnsRecordType) {
        _selectedRecordType.value = type
    }

    fun setResolver(resolver: DnsResolver) {
        _selectedResolver.value = resolver
    }

    fun setCustomResolverIp(ip: String) {
        _customResolverIp.value = ip
    }

    fun getAvailableResolvers(): List<DnsResolver> {
        val systemDns = dnsService.getSystemDnsServer()
        return if (systemDns.isNullOrEmpty()) {
            DnsService.PRESET_RESOLVERS.filter { it.primaryIp != "SYSTEM" }
        } else {
            DnsService.PRESET_RESOLVERS
        }
    }

    fun queryDns(activity: Activity? = null) {
        adService.recordToolAction(activity)
        viewModelScope.launch {
            dnsService.query(
                domain = _domainInput.value,
                recordType = _selectedRecordType.value,
                resolver = _selectedResolver.value,
                customResolverIp = _customResolverIp.value
            )
        }
    }

    fun saveResultsToHistory() {
        viewModelScope.launch {
            val state = dnsState.value
            if (state.results.isEmpty()) return@launch

            val summaryText = "DNS ${state.recordType.name} for ${state.domain}: ${state.results.size} records (${state.queryTimeMs} ms)"
            val details = buildString {
                appendLine("Domain: ${state.domain}")
                appendLine("Record Type: ${state.recordType.name}")
                appendLine("DNS Resolver: ${state.resolver.name} (${state.resolver.primaryIp})")
                appendLine("Response Time: ${state.queryTimeMs} ms")
                appendLine("\nReturned records:")
                state.results.forEach { r ->
                    appendLine("• ${r.value} (TTL: ${r.ttlSeconds}s)")
                }
            }

            historyDao.insert(
                HistoryEntity(
                    toolType = ToolType.DNS,
                    target = state.domain,
                    summary = summaryText,
                    details = details
                )
            )
        }
    }
}
