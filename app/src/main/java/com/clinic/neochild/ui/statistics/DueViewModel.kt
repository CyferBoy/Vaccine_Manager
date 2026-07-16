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
    private val reminderRepository: ReminderRepository
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow("Today")
    val selectedFilter = _selectedFilter.asStateFlow()

    val uiState: StateFlow<DueUiState> = combine(
        getPatientsUseCase(),
        reminderRepository.getDueList(),
        _selectedFilter
    ) { patients, dueVaccinations, filter ->
        
        val filtered = PatientUtils.filterVaccinationsByPeriod(dueVaccinations, filter)
        
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time
        
        val overdue = dueVaccinations.count { 
            val date = PatientUtils.parseDate(it.nextDueDate)
            date != null && date.before(todayStart)
        }

        DueUiState(
            patients = patients,
            vaccinations = dueVaccinations,
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
            val req = findMatchingRequirement(vaccination) ?: return@launch
            reminderRepository.markAsDone(req)
        }
    }

    fun clearReminder(vaccination: Vaccination) {
        viewModelScope.launch {
            val req = findMatchingRequirement(vaccination) ?: return@launch
            reminderRepository.dismissReminder(req)
        }
    }

    fun rescheduleVaccination(vaccinationId: String, newDate: String, reason: String) {
        viewModelScope.launch {
            // vaccinationId here refers to originalVisitId in our mapped Vaccination objects
            val vaccination = uiState.value.vaccinations.find { it.id == vaccinationId } ?: return@launch
            val req = findMatchingRequirement(vaccination) ?: return@launch
            reminderRepository.reschedule(req, newDate, reason)
        }
    }

    fun markVaccinatedElsewhere(
        vaccinationId: String,
        source: VaccinationSource,
        date: String,
        notes: String
    ) {
        viewModelScope.launch {
            val vaccination = uiState.value.vaccinations.find { it.id == vaccinationId } ?: return@launch
            val req = findMatchingRequirement(vaccination) ?: return@launch
            reminderRepository.markVaccinatedElsewhere(req, source, date, notes)
        }
    }

    private fun findMatchingRequirement(vaccination: Vaccination): PendingRequirement? {
        val dueDate = PatientUtils.parseDate(vaccination.nextDueDate) ?: return null
        return PendingRequirement(
            patientId = vaccination.patientId,
            vaccineName = vaccination.nxtVaccineNames.firstOrNull() ?: "",
            dueDate = dueDate,
            originalVisitId = vaccination.id
        )
    }
}
