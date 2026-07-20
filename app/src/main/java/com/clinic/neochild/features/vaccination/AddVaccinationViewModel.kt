package com.clinic.neochild.features.vaccination

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinic.neochild.core.utils.InventoryUtils
import com.clinic.neochild.data.local.entity.VaccineBatchEntity
import com.clinic.neochild.domain.model.Vaccination
import com.clinic.neochild.domain.model.Vaccine
import com.clinic.neochild.domain.usecase.vaccination.CompleteVaccinationUseCase
import com.clinic.neochild.domain.usecase.vaccination.GetVaccinationsUseCase
import com.clinic.neochild.domain.repository.InventoryRepository
import com.clinic.neochild.domain.repository.ReminderRepository
import com.clinic.neochild.features.settings.NotificationSettingsManager
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddVaccinationUiState(
    val isLoading: Boolean = false,
    val inventory: List<Vaccine> = emptyList(),
    val activeBatches: Map<String, List<VaccineBatchEntity>> = emptyMap(),
    val showExpiredBatches: Boolean = false,
    val lowStockThreshold: Int = 5,
    val error: String? = null,
    val isSaved: Boolean = false,
    val savedVaccination: Vaccination? = null,
    
    // Form State
    val patientId: String = "",
    val selectedVaccines: List<String> = emptyList(),
    val selectedVaccineIds: List<String> = emptyList(),
    val batchNumbers: List<String> = emptyList(),
    val expiryDates: List<String> = emptyList(),
    val nextBrandSearch: String = "",
    val dateGiven: String = "",
    val nextDueDate: String = "",
    val cost: String = "",
    val cashAmount: String = "",
    val onlineAmount: String = "",
    val totalPaid: Double = 0.0,
    val withFees: Boolean = false,
    val doctorsAcc: Boolean = false
)

@HiltViewModel
class AddVaccinationViewModel @Inject constructor(
    private val inventoryRepository: InventoryRepository,
    private val completeVaccinationUseCase: CompleteVaccinationUseCase,
    private val getVaccinationsUseCase: GetVaccinationsUseCase,
    private val reminderRepository: ReminderRepository,
    private val settingsManager: NotificationSettingsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddVaccinationUiState())
    val uiState: StateFlow<AddVaccinationUiState> = _uiState.asStateFlow()

    private val _vaccination = MutableStateFlow<Vaccination?>(null)
    val vaccination: StateFlow<Vaccination?> = _vaccination.asStateFlow()

    private val _showExpiredBatches = MutableStateFlow(false)
    val showExpiredBatches = _showExpiredBatches.asStateFlow()

    init {
        observeInventory()
    }

    fun toggleShowExpiredBatches(show: Boolean) {
        _showExpiredBatches.value = show
    }

    private fun observeInventory() {
        viewModelScope.launch {
            combine(
                inventoryRepository.getInventoryItems(),
                inventoryRepository.getAllBatches(),
                _showExpiredBatches,
                settingsManager.settingsFlow
            ) { items, allBatches, showExpired, settings ->
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
                }.sortedBy { it.brandName }

                val batchesMap = allBatches
                    .filter { batch -> 
                        !batch.isDeleted && 
                        batch.remainingQuantity > 0 && 
                        (showExpired || !InventoryUtils.isExpired(batch.expiryDate))
                    }
                    .groupBy { it.vaccineId }
                    .mapValues { entry -> 
                        entry.value.sortedBy { it.expiryDate } // FEFO sorting
                    }

                _uiState.value = _uiState.value.copy(
                    inventory = vaccines,
                    activeBatches = batchesMap,
                    showExpiredBatches = showExpired,
                    lowStockThreshold = settings.lowStockThreshold
                )
            }.collect()
        }
    }

    fun onPatientIdChange(id: String) {
        _uiState.update { it.copy(patientId = id) }
    }

    fun onVaccineSelected(vaccine: Vaccine, batch: VaccineBatchEntity) {
        _uiState.update { state ->
            state.copy(
                selectedVaccines = state.selectedVaccines + vaccine.brandName,
                selectedVaccineIds = state.selectedVaccineIds + vaccine.id,
                batchNumbers = state.batchNumbers + batch.batchNumber,
                expiryDates = state.expiryDates + batch.expiryDate
            )
        }
    }

    fun onRemoveVaccine(index: Int) {
        _uiState.update { state ->
            state.copy(
                selectedVaccines = state.selectedVaccines.toMutableList().apply { removeAt(index) },
                selectedVaccineIds = state.selectedVaccineIds.toMutableList().apply { removeAt(index) },
                batchNumbers = state.batchNumbers.toMutableList().apply { removeAt(index) },
                expiryDates = state.expiryDates.toMutableList().apply { removeAt(index) }
            )
        }
    }

    fun onNextBrandChange(brand: String) {
        _uiState.update { it.copy(nextBrandSearch = brand) }
    }

    fun onDateGivenChange(date: String) {
        _uiState.update { it.copy(dateGiven = date) }
    }

    fun onNextDueDateChange(date: String) {
        _uiState.update { it.copy(nextDueDate = date) }
    }

    fun onCashChange(amount: String) {
        _uiState.update { state ->
            val cash = amount.toDoubleOrNull() ?: 0.0
            val online = state.onlineAmount.toDoubleOrNull() ?: 0.0
            val total = cash + online
            state.copy(
                cashAmount = amount,
                totalPaid = total,
                cost = if (total > 0) (if (total % 1.0 == 0.0) total.toInt().toString() else total.toString()) else state.cost
            )
        }
    }

    fun onOnlineChange(amount: String) {
        _uiState.update { state ->
            val online = amount.toDoubleOrNull() ?: 0.0
            val cash = state.cashAmount.toDoubleOrNull() ?: 0.0
            val total = cash + online
            state.copy(
                onlineAmount = amount,
                totalPaid = total,
                cost = if (total > 0) (if (total % 1.0 == 0.0) total.toInt().toString() else total.toString()) else state.cost
            )
        }
    }

    fun onCostChange(amount: String) {
        _uiState.update { it.copy(cost = amount) }
    }

    fun onFeesToggle(enabled: Boolean) {
        _uiState.update { it.copy(withFees = enabled) }
    }

    fun onAccToggle(enabled: Boolean) {
        _uiState.update { it.copy(doctorsAcc = enabled) }
    }

    fun prefillForm(v: Vaccination) {
        _uiState.update { it.copy(
            patientId = v.patientId,
            selectedVaccines = v.vaccineNames,
            batchNumbers = v.batchNumbers,
            expiryDates = v.expiryDates,
            nextBrandSearch = v.nxtVaccineNames.joinToString(", "),
            dateGiven = v.dateGiven,
            nextDueDate = v.nextDueDate,
            cost = if (v.cost % 1.0 == 0.0) v.cost.toInt().toString() else v.cost.toString(),
            cashAmount = if (v.cashAmount % 1.0 == 0.0) v.cashAmount.toInt().toString() else v.cashAmount.toString(),
            onlineAmount = if (v.onlineAmount % 1.0 == 0.0) v.onlineAmount.toInt().toString() else v.onlineAmount.toString(),
            totalPaid = v.totalPaid,
            withFees = v.withFees,
            doctorsAcc = v.doctorsAcc
        ) }
    }

    fun initializeDates(today: String) {
        if (_uiState.value.dateGiven.isEmpty()) {
            _uiState.update { it.copy(dateGiven = today) }
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
