package com.clinic.neochild.features.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinic.neochild.core.constants.Constants
import com.clinic.neochild.data.local.entity.VaccineBatchEntity
import com.clinic.neochild.data.local.entity.VaccineEntity
import com.clinic.neochild.domain.model.BatchStatus
import com.clinic.neochild.domain.model.InventoryItem
import com.clinic.neochild.domain.repository.InventoryRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class AddVaccineStockUiState(
    val isLoading: Boolean = false,
    val allItems: List<InventoryItem> = emptyList(),
    val isSaved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AddVaccineStockViewModel @Inject constructor(
    private val inventoryRepository: InventoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddVaccineStockUiState())
    val uiState: StateFlow<AddVaccineStockUiState> = _uiState.asStateFlow()

    private val _editingBatch = MutableStateFlow<VaccineBatchEntity?>(null)
    val editingBatch = _editingBatch.asStateFlow()

    init {
        observeInventory()
    }

    fun loadBatch(batchId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            inventoryRepository.getBatchById(batchId)?.let {
                _editingBatch.value = it
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private fun observeInventory() {
        viewModelScope.launch {
            inventoryRepository.getInventoryItems().collect { items ->
                _uiState.update { it.copy(allItems = items) }
            }
        }
    }

    fun saveStock(
        editingBatchId: String?,
        type: String,
        brandName: String,
        companyName: String,
        batchNumber: String,
        quantity: Int,
        expiryDate: String,
        mrp: Double,
        netRate: Double
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val user = FirebaseAuth.getInstance().currentUser?.email ?: "Unknown"
                
                // Find if vaccine already exists in our list
                val existingItem = _uiState.value.allItems.find { 
                    it.brandName.equals(brandName, true) && it.type.equals(type, true) 
                }

                val vaccineId = existingItem?.id ?: UUID.randomUUID().toString()
                val vaccine = VaccineEntity(
                    id = vaccineId,
                    type = type,
                    brandName = brandName,
                    companyName = companyName
                )

                if (editingBatchId != null) {
                    val batch = _editingBatch.value ?: throw IllegalStateException("Batch not loaded")
                    val updatedBatch = batch.copy(
                        vaccineId = vaccineId,
                        batchNumber = batchNumber,
                        manufacturer = companyName,
                        expiryDate = expiryDate,
                        remainingQuantity = quantity,
                        purchaseCost = netRate,
                        sellingPrice = mrp
                    )
                    inventoryRepository.updateBatch(updatedBatch, user)
                } else {
                    val batchId = UUID.randomUUID().toString()
                    val today = SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(Date())
                    val batch = VaccineBatchEntity(
                        batchId = batchId,
                        vaccineId = vaccineId,
                        batchNumber = batchNumber,
                        manufacturer = companyName,
                        purchaseDate = today,
                        expiryDate = expiryDate,
                        purchaseQuantity = quantity,
                        remainingQuantity = quantity,
                        supplier = "Manual Entry",
                        purchaseCost = netRate,
                        sellingPrice = mrp,
                        status = BatchStatus.ACTIVE.name
                    )
                    inventoryRepository.addStock(vaccine, batch, user)
                }
                
                _uiState.update { it.copy(isSaved = true, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun deleteBatch(batchId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val user = FirebaseAuth.getInstance().currentUser?.email ?: "Unknown"
                inventoryRepository.deleteBatch(batchId, user)
                _uiState.update { it.copy(isSaved = true, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun resetSaveState() {
        _uiState.update { it.copy(isSaved = false, error = null) }
    }
}
