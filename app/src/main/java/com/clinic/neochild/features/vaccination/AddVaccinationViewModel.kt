package com.clinic.neochild.features.vaccination

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinic.neochild.domain.model.Vaccination
import com.clinic.neochild.domain.model.Vaccine
import com.clinic.neochild.domain.usecase.vaccination.CompleteVaccinationUseCase
import com.clinic.neochild.domain.usecase.vaccination.GetVaccinationsUseCase
import com.clinic.neochild.domain.repository.InventoryRepository
import com.clinic.neochild.domain.repository.ReminderRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddVaccinationUiState(
    val isLoading: Boolean = false,
    val inventory: List<Vaccine> = emptyList(),
    val error: String? = null,
    val isSaved: Boolean = false,
    val savedVaccination: Vaccination? = null
)

@HiltViewModel
class AddVaccinationViewModel @Inject constructor(
    private val inventoryRepository: InventoryRepository,
    private val completeVaccinationUseCase: CompleteVaccinationUseCase,
    private val getVaccinationsUseCase: GetVaccinationsUseCase,
    private val reminderRepository: ReminderRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddVaccinationUiState())
    val uiState: StateFlow<AddVaccinationUiState> = _uiState.asStateFlow()

    private val _vaccination = MutableStateFlow<Vaccination?>(null)
    val vaccination: StateFlow<Vaccination?> = _vaccination.asStateFlow()

    init {
        observeInventory()
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
                        batchNumber = "", // Individual batch info is handled in deduction logic
                        expiryDate = "",
                        mrp = 0.0,
                        netRate = 0.0
                    )
                }
                _uiState.value = _uiState.value.copy(inventory = vaccines)
            }
        }
    }

    fun loadVaccination(id: String) {
        viewModelScope.launch {
            getVaccinationsUseCase().collect { all ->
                _vaccination.value = all.find { it.id == id }
            }
        }
    }

    fun saveVaccination(
        vaccination: Vaccination,
        isNew: Boolean,
        selectedVaccineIds: List<String>,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val user = FirebaseAuth.getInstance().currentUser?.email ?: "Unknown"
                completeVaccinationUseCase(vaccination, isNew, selectedVaccineIds, user)
                _uiState.value = _uiState.value.copy(isSaved = true, savedVaccination = vaccination)
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun resetSaveState() {
        _uiState.value = _uiState.value.copy(isSaved = false, savedVaccination = null)
    }
}
