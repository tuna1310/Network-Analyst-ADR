package com.networkanalyst.app.ui.screens.whois

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.networkanalyst.app.data.local.HistoryDao
import com.networkanalyst.app.data.local.entity.HistoryEntity
import com.networkanalyst.app.data.model.ToolType
import com.networkanalyst.app.data.model.WhoisResult
import com.networkanalyst.app.data.service.AdService
import com.networkanalyst.app.data.service.WhoisRdapService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WhoisUiState(
    val isLoading: Boolean = false,
    val target: String = "google.com",
    val result: WhoisResult? = null,
    val selectedTab: Int = 0, // 0 = Summary, 1 = Raw
    val errorMessage: String? = null
)

@HiltViewModel
class WhoisViewModel @Inject constructor(
    private val whoisRdapService: WhoisRdapService,
    val adService: AdService,
    private val historyDao: HistoryDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(WhoisUiState())
    val uiState: StateFlow<WhoisUiState> = _uiState.asStateFlow()

    fun setTarget(target: String) {
        _uiState.value = _uiState.value.copy(target = target)
    }

    fun setSelectedTab(tab: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    fun queryWhois(activity: Activity? = null) {
        val target = _uiState.value.target.trim()
        if (target.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Vui lòng nhập tên miền, địa chỉ IP hoặc ASN hợp lệ.")
            return
        }

        adService.recordToolAction(activity)
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            try {
                val res = whoisRdapService.query(target)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    result = res,
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Lỗi khi tra cứu: ${e.localizedMessage}"
                )
            }
        }
    }

    fun saveResultToHistory() {
        val res = _uiState.value.result ?: return
        viewModelScope.launch {
            val summary = res.summary
            val summaryText = buildString {
                append("Whois ${res.target}: ")
                if (!summary.entityName.isNullOrEmpty()) append("Chủ sở hữu: ${summary.entityName} | ")
                if (!summary.registrar.isNullOrEmpty()) append("Registrar: ${summary.registrar} | ")
                append("Nguồn: ${res.source.name}")
            }

            val details = buildString {
                appendLine("Mục tiêu: ${res.target}")
                appendLine("Loại: ${res.queryType.name} | Nguồn: ${res.source.name} | Thời gian: ${res.queryTimeMs}ms")
                if (!summary.entityName.isNullOrEmpty()) appendLine("Chủ sở hữu/Thực thể: ${summary.entityName}")
                if (!summary.registrar.isNullOrEmpty()) appendLine("Nhà đăng ký (Registrar): ${summary.registrar}")
                if (!summary.createdDate.isNullOrEmpty()) appendLine("Ngày tạo: ${summary.createdDate}")
                if (!summary.expiresDate.isNullOrEmpty()) appendLine("Ngày hết hạn: ${summary.expiresDate}")
                if (!summary.updatedDate.isNullOrEmpty()) appendLine("Ngày cập nhật: ${summary.updatedDate}")
                if (summary.statuses.isNotEmpty()) appendLine("Trạng thái: ${summary.statuses.joinToString(", ")}")
                if (!summary.cidr.isNullOrEmpty()) appendLine("CIDR: ${summary.cidr}")
                if (summary.nameservers.isNotEmpty()) appendLine("Nameservers: ${summary.nameservers.joinToString(", ")}")
                if (!summary.contactEmail.isNullOrEmpty()) appendLine("Email liên hệ: ${summary.contactEmail}")
                if (!summary.asnNumber.isNullOrEmpty()) appendLine("ASN: ${summary.asnNumber} (${summary.asnName ?: ""})")
            }

            historyDao.insert(
                HistoryEntity(
                    toolType = ToolType.WHOIS,
                    target = res.target,
                    summary = summaryText,
                    details = details
                )
            )
        }
    }
}
