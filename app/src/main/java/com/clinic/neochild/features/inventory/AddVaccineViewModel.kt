package com.clinic.neochild.features.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinic.neochild.data.local.entity.VaccineEntity
import com.clinic.neochild.domain.repository.InventoryRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

data class AddVaccineUiState(
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null,
    val vaccine: VaccineEntity? = null
)

@HiltViewModel
class AddVaccineViewModel @Inject constructor(
    private val inventoryRepository: InventoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddVaccineUiState())
    val uiState: StateFlow<AddVaccineUiState> = _uiState.asStateFlow()

    fun loadVaccine(vaccineId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            // Assuming we have a way to get vaccine by ID, or we can use getInventoryItems and find
            inventoryRepository.getInventoryItems().collect { items ->
                val item = items.find { it.id == vaccineId }
                if (item != null) {
                    val entity = VaccineEntity(
                        id = item.id,
                        type = item.type,
                        brandName = item.brandName,
                        companyName = item.company
                    )
                    _uiState.update { it.copy(vaccine = entity, isLoading = false) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Vaccine not found") }
                }
            }
        }
    }

    fun saveVaccine(
        id: String?,
        brandName: String,
        type: String,
        companyName: String
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val user = FirebaseAuth.getInstance().currentUser?.email ?: "Unknown"
                val vaccineId = id ?: UUID.randomUUID().toString()
                val vaccine = VaccineEntity(
                    id = vaccineId,
                    brandName = brandName,
                    type = type,
                    companyName = companyName
                )

                if (id != null) {
                    inventoryRepository.updateVaccine(vaccine, user)
                } else {
                    inventoryRepository.addVaccine(vaccine, user)
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
