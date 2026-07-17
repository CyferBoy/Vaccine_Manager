package com.clinic.neochild.ui.vaccine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinic.neochild.domain.model.Vaccine
import com.clinic.neochild.domain.model.WasteRecord
import com.clinic.neochild.domain.repository.InventoryRepository
import com.clinic.neochild.domain.repository.WasteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class WasteUiState(
    val wasteRecords: List<WasteRecord> = emptyList(),
    val inventory: List<Vaccine> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class WasteViewModel @Inject constructor(
    private val wasteRepository: WasteRepository,
    private val inventoryRepository: InventoryRepository
) : ViewModel() {

    private val _isSaving = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<WasteUiState> = combine(
        wasteRepository.getAllWaste(),
        inventoryRepository.getAllVaccines(),
        _isSaving,
        _error
    ) { waste, vaccines, saving, err ->
        WasteUiState(
            wasteRecords = waste,
            inventory = vaccines.map { 
                Vaccine(
                    id = it.id,
                    brandName = it.brandName,
                    type = it.type,
                    companyName = it.companyName
                )
            },
            isLoading = false,
            isSaving = saving,
            error = err
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WasteUiState(isLoading = true))

    fun recordWaste(
        vaccineId: String,
        brandName: String,
        batchNumber: String,
        expiryDate: String,
        dateWasted: String,
        reason: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val record = WasteRecord(
                id = UUID.randomUUID().toString(),
                vaccineId = vaccineId,
                brandName = brandName,
                batchNumber = batchNumber,
                expiryDate = expiryDate,
                dateWasted = dateWasted,
                reason = reason,
                quantity = 1
            )
            
            try {
                // In a real app, user would be from Auth. Using fallback for now.
                wasteRepository.recordWaste(record, "System User")
                onSuccess()
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun deleteWaste(id: String) {
        viewModelScope.launch {
            wasteRepository.deleteWaste(id)
        }
    }

    fun clearError() {
        _error.value = null
    }
}
