package com.clinic.neochild.features.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinic.neochild.core.constants.Constants
import com.clinic.neochild.data.local.entity.VaccineBatchEntity
import com.clinic.neochild.data.local.entity.VaccineEntity
import com.clinic.neochild.domain.model.BatchStatus
import com.clinic.neochild.domain.model.Vaccine
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
    val allVaccines: List<Vaccine> = emptyList(),
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
            val batch = inventoryRepository.getAllBatches().first().find { it.batchId == batchId }
            _editingBatch.value = batch
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private fun observeInventory() {
        viewModelScope.launch {
            inventoryRepository.getInventoryItems().collect { items ->
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
                }
                _uiState.update { it.copy(allVaccines = vaccines) }
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
                
                // 1. Find or Create Vaccine Definition
                val existingVaccines = inventoryRepository.getAllVaccines().first()
                val def = existingVaccines.find { 
                    it.brandName.equals(brandName, true) && it.type.equals(type, true) 
                }

                val finalVaccineId = if (def == null) {
                    val newId = UUID.randomUUID().toString()
                    val newDef = VaccineEntity(
                        id = newId,
                        type = type,
                        brandName = brandName,
                        companyName = companyName
                    )
                    inventoryRepository.addVaccineDefinition(newDef)
                    newId
                } else {
                    if (def.companyName != companyName) {
                        inventoryRepository.updateVaccineDefinition(def.copy(companyName = companyName))
                    }
                    def.id
                }

                // 2. Add or Update Batch
                if (editingBatchId != null) {
                    val batch = inventoryRepository.getAllBatches().first().find { it.batchId == editingBatchId }
                    if (batch != null) {
                        val updatedBatch = batch.copy(
                            vaccineId = finalVaccineId,
                            batchNumber = batchNumber,
                            manufacturer = companyName,
                            expiryDate = expiryDate,
                            remainingQuantity = quantity, // Assuming direct update for now
                            purchaseCost = netRate,
                            sellingPrice = mrp
                        )
                        inventoryRepository.addBatch(updatedBatch, user) // insertBatch uses REPLACE
                    }
                } else {
                    val batchId = UUID.randomUUID().toString()
                    val today = SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(Date())
                    
                    val batch = VaccineBatchEntity(
                        batchId = batchId,
                        vaccineId = finalVaccineId,
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
                    inventoryRepository.addBatch(batch, user)
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
