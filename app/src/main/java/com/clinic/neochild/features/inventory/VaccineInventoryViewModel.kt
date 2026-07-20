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
                inventoryRepository.getAllBatches(),
                settingsManager.settingsFlow
            ) { items, allBatches, settings ->
                val vaccines = allBatches.filter { !it.isDeleted }.map { batch ->
                    val def = items.find { it.id == batch.vaccineId }
                    Vaccine(
                        id = batch.batchId,
                        type = def?.type ?: "Unknown",
                        brandName = def?.brandName ?: "Unknown",
                        companyName = def?.company ?: "",
                        stock = batch.remainingQuantity,
                        batchNumber = batch.batchNumber,
                        expiryDate = batch.expiryDate,
                        mrp = batch.sellingPrice,
                        netRate = batch.purchaseCost.toDouble()
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
