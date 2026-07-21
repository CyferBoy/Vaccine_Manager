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
                val vaccines = items.flatMap { item ->
                    if (item.batches.isEmpty()) {
                        // Still show vaccines with 0 stock as a placeholder if needed, 
                        // or just skip. The UI currently shows groups based on this list.
                        listOf(
                            Vaccine(
                                id = item.id, // This will be the vaccineId
                                type = item.type,
                                brandName = item.brandName,
                                companyName = item.company,
                                stock = 0,
                                batchNumber = "No Stock",
                                expiryDate = "",
                                mrp = 0.0,
                                netRate = 0.0
                            )
                        )
                    } else {
                        item.batches.map { batch ->
                            Vaccine(
                                id = batch.batchId, // Now it's the BATCH ID
                                type = item.type,
                                brandName = item.brandName,
                                companyName = item.company,
                                stock = batch.remainingQuantity,
                                batchNumber = batch.batchNumber,
                                expiryDate = batch.expiryDate,
                                mrp = batch.sellingPrice,
                                netRate = batch.purchaseCost
                            )
                        }
                    }
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

    fun deleteBatch(vaccine: Vaccine) {
        viewModelScope.launch {
            try {
                val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: "Unknown"
                // The Vaccine model's id is actually the batchId in the inventory list
                inventoryRepository.deleteBatch(vaccine.id, user)
            } catch (_: Exception) {
                // Handle error
            }
        }
    }
}
