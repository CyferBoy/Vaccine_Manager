package com.clinic.neochild.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinic.neochild.data.model.Patient
import com.clinic.neochild.data.model.Vaccination
import com.clinic.neochild.data.model.VaccinationSource
import com.clinic.neochild.domain.repository.ReminderRepository
import com.clinic.neochild.domain.repository.VaccineRepository
import com.clinic.neochild.domain.usecase.patient.GetPatientsUseCase
import com.clinic.neochild.domain.usecase.vaccination.GetVaccinationsUseCase
import com.clinic.neochild.domain.usecase.vaccination.SaveVaccinationUseCase
import com.clinic.neochild.notification.ReminderScheduler
import com.clinic.neochild.utils.PatientUtils
import com.clinic.neochild.utils.ReminderEngine
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

data class DueUiState(
    val patients: List<Patient> = emptyList(),
    val vaccinations: List<Vaccination> = emptyList(),
    val filteredVaccinations: List<Vaccination> = emptyList(),
    val isLoading: Boolean = false,
    val selectedFilter: String = "Today",
    val overdueCount: Int = 0
)

@HiltViewModel
class DueViewModel @Inject constructor(
    private val getPatientsUseCase: GetPatientsUseCase,
    private val getVaccinationsUseCase: GetVaccinationsUseCase,
    private val saveVaccinationUseCase: SaveVaccinationUseCase,
    private val vaccineRepository: VaccineRepository,
    private val reminderRepository: ReminderRepository,
    private val reminderScheduler: ReminderScheduler,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow("Today")
    val selectedFilter = _selectedFilter.asStateFlow()

    val uiState: StateFlow<DueUiState> = combine(
        getPatientsUseCase(),
        getVaccinationsUseCase(),
        _selectedFilter
    ) { patients, vaccinations, filter ->
        // Use the new ReminderEngine for more accurate tracking
        val unsatisfied = ReminderEngine.getUnsatisfiedRequirements(vaccinations)
        
        // Group unsatisfied requirements by patient and due date to avoid duplicate entries for the same visit
        val pending = unsatisfied.groupBy { it.patientId + PatientUtils.formatDate(it.dueDate) }
            .mapNotNull { (_, reqs) ->
                val first = reqs.first()
                vaccinations.find { it.id == first.originalVisitId }?.copy(
                    nxtVaccineNames = reqs.map { it.vaccineName },
                    nextDueDate = PatientUtils.formatDate(first.dueDate)
                )
            }

        val filtered = PatientUtils.filterVaccinationsByPeriod(pending, filter)
        
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)
        val todayStart = calendar.time
        
        val overdue = pending.count { 
            val date = PatientUtils.parseDate(it.nextDueDate)
            date != null && date.before(todayStart)
        }

        DueUiState(
            patients = patients,
            vaccinations = vaccinations,
            filteredVaccinations = filtered,
            isLoading = false,
            selectedFilter = filter,
            overdueCount = overdue
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DueUiState(isLoading = true))

    fun updateFilter(filter: String) {
        _selectedFilter.value = filter
    }

    fun markAsDone(vaccination: Vaccination) {
        viewModelScope.launch {
            val user = auth.currentUser?.email ?: "Unknown"
            // Create a new vaccination record for today representing the administered vaccines
            val today = PatientUtils.formatDate(Date())
            val newVaccination = Vaccination(
                id = UUID.randomUUID().toString(),
                patientId = vaccination.patientId,
                vaccineNames = vaccination.nxtVaccineNames,
                dateGiven = today,
                isDone = true,
                source = VaccinationSource.CLINIC.name,
                performedBy = user
            )
            saveVaccinationUseCase(newVaccination)
            
            // Try to deduct inventory for the given vaccines
            tryUpdateInventory(vaccination.nxtVaccineNames)
            
            reminderRepository.markPatientRemindersCompleted(vaccination.patientId)
            reminderScheduler.runNow()
        }
    }

    private suspend fun tryUpdateInventory(vaccineNames: List<String>) {
        try {
            val inventory = vaccineRepository.getInventory().first()
            vaccineNames.forEach { name ->
                val cleaned = PatientUtils.cleanVaccineName(name).lowercase().trim()
                val match = inventory.find { it.brandName.lowercase().trim() == cleaned || it.type.lowercase().trim() == cleaned }
                if (match != null && match.stock > 0) {
                    vaccineRepository.updateStock(match.id, match.stock - 1)
                }
            }
        } catch (e: Exception) {
            // Log error or ignore for best-effort inventory update
        }
    }

    fun rescheduleVaccination(vaccinationId: String, newDate: String, reason: String) {
        viewModelScope.launch {
            val user = auth.currentUser?.email ?: "Unknown"
            val original = uiState.value.vaccinations.find { it.id == vaccinationId }
            if (original != null) {
                // Update the original record's nextDueDate to the new date
                saveVaccinationUseCase(original.copy(
                    nextDueDate = newDate,
                    rescheduleReason = reason,
                    performedBy = user
                ))
                reminderRepository.markPatientRemindersCompleted(original.patientId)
                reminderScheduler.runNow()
            }
        }
    }

    fun markVaccinatedElsewhere(
        vaccinationId: String,
        source: VaccinationSource,
        date: String,
        notes: String
    ) {
        viewModelScope.launch {
            val user = auth.currentUser?.email ?: "Unknown"
            val original = uiState.value.vaccinations.find { it.id == vaccinationId }
            if (original != null) {
                // Find unsatisfied vaccines for this specific visit
                val unsatisfied = ReminderEngine.getUnsatisfiedRequirements(uiState.value.vaccinations)
                    .filter { it.originalVisitId == vaccinationId }
                    .map { it.vaccineName }

                if (unsatisfied.isNotEmpty()) {
                    val newVaccination = Vaccination(
                        id = UUID.randomUUID().toString(),
                        patientId = original.patientId,
                        vaccineNames = unsatisfied,
                        dateGiven = date,
                        isDone = true,
                        source = source.name,
                        notes = notes,
                        performedBy = user
                    )
                    saveVaccinationUseCase(newVaccination)
                    reminderRepository.markPatientRemindersCompleted(original.patientId)
                    reminderScheduler.runNow()
                }
            }
        }
    }
}
