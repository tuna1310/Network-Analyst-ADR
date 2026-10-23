package com.networkanalyst.app.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.networkanalyst.app.data.local.HistoryDao
import com.networkanalyst.app.data.local.entity.HistoryEntity
import com.networkanalyst.app.data.model.ToolType
import com.networkanalyst.app.data.service.AdService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyDao: HistoryDao,
    val adService: AdService
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow<ToolType?>(null)
    val selectedFilter: StateFlow<ToolType?> = _selectedFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val historyList: StateFlow<List<HistoryEntity>> = combine(
        _selectedFilter,
        _searchQuery
    ) { filter, query ->
        filter to query
    }.flatMapLatest { (filter, query) ->
        when {
            query.isNotBlank() -> historyDao.searchHistory(query)
            filter != null -> historyDao.getHistoryByTool(filter)
            else -> historyDao.getAllHistory()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(filter: ToolType?) {
        _selectedFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun deleteItem(id: Long) {
        viewModelScope.launch {
            historyDao.deleteById(id)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            val filter = _selectedFilter.value
            if (filter != null) {
                historyDao.clearByTool(filter)
            } else {
                historyDao.clearAll()
            }
        }
    }
}
