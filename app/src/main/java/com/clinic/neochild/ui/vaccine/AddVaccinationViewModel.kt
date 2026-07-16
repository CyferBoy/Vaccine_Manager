package com.clinic.neochild.ui.vaccine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinic.neochild.data.model.Vaccination
import com.clinic.neochild.data.model.Vaccine
import com.clinic.neochild.domain.usecase.vaccination.CompleteVaccinationUseCase
import com.clinic.neochild.domain.usecase.vaccination.GetVaccinationsUseCase
import com.clinic.neochild.utils.Constants
import com.clinic.neochild.utils.FirestoreMappers
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
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
    private val firestore: FirebaseFirestore,
    private val completeVaccinationUseCase: CompleteVaccinationUseCase,
    private val getVaccinationsUseCase: GetVaccinationsUseCase,
    private val reminderRepository: com.clinic.neochild.domain.repository.ReminderRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddVaccinationUiState())
    val uiState: StateFlow<AddVaccinationUiState> = _uiState.asStateFlow()

    private val _vaccination = MutableStateFlow<Vaccination?>(null)
    val vaccination: StateFlow<Vaccination?> = _vaccination.asStateFlow()

    init {
        fetchInventory()
    }

    private fun fetchInventory() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        firestore.collection("inventory").get().addOnSuccessListener { result ->
            val inventory = result.documents.mapNotNull { FirestoreMappers.toVaccine(it) }
            _uiState.value = _uiState.value.copy(inventory = inventory, isLoading = false)
        }.addOnFailureListener { e ->
            _uiState.value = _uiState.value.copy(error = e.message, isLoading = false)
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
                completeVaccinationUseCase(vaccination, isNew, selectedVaccineIds)
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
