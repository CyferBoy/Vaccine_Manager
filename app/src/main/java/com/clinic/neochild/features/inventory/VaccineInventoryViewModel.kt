package com.clinic.neochild.features.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinic.neochild.domain.model.InventoryFilter
import com.clinic.neochild.domain.model.InventoryItem
import com.clinic.neochild.domain.model.InventorySort
import com.clinic.neochild.domain.repository.InventoryRepository
import com.clinic.neochild.features.settings.NotificationSettingsManager
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VaccineInventoryUiState(
    val inventory: List<InventoryItem> = emptyList(),
    val searchQuery: String = "",
    val filter: InventoryFilter = InventoryFilter.ALL,
    val sort: InventorySort = InventorySort.ALPHABETICAL,
    val isLoading: Boolean = true
)

@HiltViewModel
class VaccineInventoryViewModel @Inject constructor(
    private val inventoryRepository: InventoryRepository,
    private val settingsManager: NotificationSettingsManager
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _filter = MutableStateFlow(InventoryFilter.ALL)
    private val _sort = MutableStateFlow(InventorySort.ALPHABETICAL)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<VaccineInventoryUiState> = combine(
        _searchQuery,
        _filter,
        _sort
    ) { query, filter, sort ->
        Triple(query, filter, sort)
    }.flatMapLatest { (query, filter, sort) ->
        inventoryRepository.getInventoryItems(query, filter, sort)
            .map { items ->
                VaccineInventoryUiState(
                    inventory = items,
                    searchQuery = query,
                    filter = filter,
                    sort = sort,
                    isLoading = false
                )
            }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VaccineInventoryUiState()
    )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onFilterChange(filter: InventoryFilter) {
        _filter.value = filter
    }

    fun onSortChange(sort: InventorySort) {
        _sort.value = sort
    }

    fun deleteBatch(batchId: String) {
        viewModelScope.launch {
            try {
                val user = FirebaseAuth.getInstance().currentUser?.email ?: "Unknown"
                inventoryRepository.deleteBatch(batchId, user)
            } catch (_: Exception) { }
        }
    }
}
