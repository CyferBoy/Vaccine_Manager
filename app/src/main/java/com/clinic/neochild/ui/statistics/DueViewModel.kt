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
import com.clinic.neochild.utils.PatientUtils
import com.clinic.neochild.utils.PendingRequirement
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
    private val reminderRepository: ReminderRepository,
    private val vaccinationRepository: com.clinic.neochild.domain.repository.VaccinationRepository
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow("Today")
    val selectedFilter = _selectedFilter.asStateFlow()

    // Requirements with original vaccination context for the UI
    private val pendingRequirements = reminderRepository.getUnsatisfiedRequirements()

    val uiState: StateFlow<DueUiState> = combine(
        getPatientsUseCase(),
        getVaccinationsUseCase(),
        pendingRequirements,
        _selectedFilter
    ) { patients, allVaccinations, requirements, filter ->
        
        // 1. Map current pending requirements to UI objects
        val pending = requirements.groupBy { it.patientId + PatientUtils.formatDate(it.dueDate) }
            .mapNotNull { (_, reqs) ->
                val first = reqs.first()
                allVaccinations.find { it.id == first.originalVisitId }?.copy(
                    nxtVaccineNames = reqs.map { it.vaccineName },
                    nextDueDate = PatientUtils.formatDate(first.dueDate),
                    isDone = false
                )
            }
        
        // Filter by period
        val filtered = PatientUtils.filterVaccinationsByPeriod(pending, filter)
        
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time
        
        val overdue = pending.count { 
            val date = PatientUtils.parseDate(it.nextDueDate)
            date != null && date.before(todayStart)
        }

        DueUiState(
            patients = patients,
            vaccinations = allVaccinations,
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
            // 1. Mark the vaccination record itself as done
            vaccinationRepository.markAsDone(vaccination.id)
            
            // 2. Clear associated reminders
            val requirements = findMatchingRequirements(vaccination)
            for (req in requirements) {
                reminderRepository.markAsDone(req)
            }
        }
    }

    fun clearReminder(vaccination: Vaccination) {
        viewModelScope.launch {
            // Find the specific requirements that match this UI item
            val requirements = findMatchingRequirements(vaccination)
            for (req in requirements) {
                reminderRepository.markAsDone(req)
            }
        }
    }

    fun rescheduleVaccination(vaccinationId: String, newDate: String, reason: String) {
        viewModelScope.launch {
            val requirements = findMatchingRequirementsById(vaccinationId)
            for (req in requirements) {
                reminderRepository.reschedule(req, newDate, reason)
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
            val requirements = findMatchingRequirementsById(vaccinationId)
            for (req in requirements) {
                reminderRepository.markVaccinatedElsewhere(req, source, date, notes)
            }
        }
    }

    private fun findMatchingRequirements(vaccination: Vaccination): List<PendingRequirement> {
        // Due to the UI mapping, we need to find the underlying requirement(s)
        val allRequirements = ReminderEngine.getUnsatisfiedRequirements(uiState.value.vaccinations)
        return allRequirements.filter { req ->
            req.patientId == vaccination.patientId && 
            PatientUtils.formatDate(req.dueDate) == vaccination.nextDueDate &&
            vaccination.nxtVaccineNames.contains(req.vaccineName)
        }
    }

    private fun findMatchingRequirementsById(vaccinationId: String): List<PendingRequirement> {
        val allRequirements = ReminderEngine.getUnsatisfiedRequirements(uiState.value.vaccinations)
        return allRequirements.filter { it.originalVisitId == vaccinationId }
    }
}
