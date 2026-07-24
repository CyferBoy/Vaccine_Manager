package com.clinic.neochild.features.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinic.neochild.core.constants.Constants
import com.clinic.neochild.data.local.entity.VaccineBatchEntity
import com.clinic.neochild.domain.model.BatchStatus
import com.clinic.neochild.domain.repository.InventoryRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class AddBatchUiState(
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null,
    val batch: VaccineBatchEntity? = null
)

@HiltViewModel
class AddBatchViewModel @Inject constructor(
    private val inventoryRepository: InventoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddBatchUiState())
    val uiState: StateFlow<AddBatchUiState> = _uiState.asStateFlow()

    fun loadBatch(batchId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val batch = inventoryRepository.getBatchById(batchId)
            if (batch != null) {
                _uiState.update { it.copy(batch = batch, isLoading = false) }
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Batch not found") }
            }
        }
    }

    fun saveBatch(
        batchId: String?,
        vaccineId: String,
        batchNumber: String,
        quantity: Int,
        expiryDate: String,
        mrp: Double,
        netRate: Double,
        manufacturer: String
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val user = FirebaseAuth.getInstance().currentUser?.email ?: "Unknown"
                
                if (batchId != null) {
                    val existing = _uiState.value.batch ?: throw IllegalStateException("Batch not loaded")
                    val updated = existing.copy(
                        batchNumber = batchNumber,
                        remainingQuantity = quantity,
                        expiryDate = expiryDate,
                        sellingPrice = mrp,
                        purchaseCost = netRate,
                        manufacturer = manufacturer
                    )
                    inventoryRepository.updateBatch(updated, user)
                } else {
                    val newBatchId = UUID.randomUUID().toString()
                    val today = SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(Date())
                    val batch = VaccineBatchEntity(
                        batchId = newBatchId,
                        vaccineId = vaccineId,
                        batchNumber = batchNumber,
                        manufacturer = manufacturer,
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

    fun resetState() {
        _uiState.update { it.copy(isSaved = false, error = null) }
    }
}
