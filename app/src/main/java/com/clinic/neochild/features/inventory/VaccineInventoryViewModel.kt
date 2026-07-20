package com.clinic.neochild.features.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinic.neochild.domain.model.Vaccine
import com.clinic.neochild.domain.repository.InventoryRepository
import com.clinic.neochild.features.settings.NotificationSettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VaccineInventoryUiState(
    val vaccines: List<Vaccine> = emptyList(),
    val lowStockThreshold: Int = 5,
    val isLoading: Boolean = true
)

@HiltViewModel
class VaccineInventoryViewModel @Inject constructor(
    private val inventoryRepository: InventoryRepository,
    private val settingsManager: NotificationSettingsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(VaccineInventoryUiState())
    val uiState: StateFlow<VaccineInventoryUiState> = _uiState.asStateFlow()

    init {
        observeInventory()
    }

    private fun observeInventory() {
        viewModelScope.launch {
            combine(
                inventoryRepository.getInventoryItems(),
                settingsManager.settingsFlow
            ) { items, settings ->
                val vaccines = items.map { item ->
                    Vaccine(
                        id = item.id,
                        type = item.type,
                        brandName = item.brandName,
                        companyName = item.company,
                        stock = item.stock,
                        batchNumber = "",
                        expiryDate = "",
                        mrp = 0.0,
                        netRate = 0.0
                    )
                }.sortedBy { it.brandName.lowercase() }

                VaccineInventoryUiState(
                    vaccines = vaccines,
                    lowStockThreshold = settings.lowStockThreshold,
                    isLoading = false
                )
            }.collect {
                _uiState.value = it
            }
        }
    }
}
