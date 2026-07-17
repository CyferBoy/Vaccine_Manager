package com.clinic.neochild.features.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinic.neochild.domain.model.Patient
import com.clinic.neochild.domain.model.ReminderStatus
import com.clinic.neochild.domain.model.Vaccination
import com.clinic.neochild.domain.model.VaccinationSource
import com.clinic.neochild.domain.model.PendingRequirement
import com.clinic.neochild.domain.repository.ReminderRepository
import com.clinic.neochild.domain.usecase.patient.GetPatientsUseCase
import com.clinic.neochild.domain.usecase.vaccination.CompleteVaccinationUseCase
import com.clinic.neochild.core.utils.PatientUtils
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
    private val completeVaccinationUseCase: CompleteVaccinationUseCase,
    private val reminderRepository: ReminderRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow("Today")
    val selectedFilter = _selectedFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val currentUserEmail: String
        get() = auth.currentUser?.email ?: "Unknown Staff"

    val uiState: StateFlow<DueUiState> = combine(
        getPatientsUseCase(),
        _searchQuery,
        _selectedFilter
    ) { patients, query, filter ->
        
        val filterStatus = when (filter) {
            "Completed" -> listOf(ReminderStatus.COMPLETED)
            "Dismissed" -> listOf(ReminderStatus.DISMISSED)
            else -> null
        }

        val dueVaccinations = reminderRepository.getDueList(query, filterStatus).first()
        
        val filtered = if (filter == "Completed" || filter == "Dismissed") {
            dueVaccinations 
        } else {
            PatientUtils.filterVaccinationsByPeriod(dueVaccinations, filter)
        }
        
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

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun markAsDone(vaccination: Vaccination) {
        viewModelScope.launch {
            val req = findMatchingRequirement(vaccination) ?: return@launch
            completeVaccinationUseCase.fromRequirement(req, currentUserEmail)
        }
    }

    fun dismissReminder(vaccination: Vaccination, reason: String) {
        viewModelScope.launch {
            val req = findMatchingRequirement(vaccination) ?: return@launch
            reminderRepository.dismissReminder(req, reason, currentUserEmail)
        }
    }

    fun rescheduleVaccination(vaccination: Vaccination, newDate: String, reason: String) {
        viewModelScope.launch {
            val req = findMatchingRequirement(vaccination) ?: return@launch
            reminderRepository.reschedule(req, newDate, reason, currentUserEmail)
        }
    }

    fun markVaccinatedElsewhere(
        vaccination: Vaccination,
        source: VaccinationSource,
        date: String,
        notes: String
    ) {
        viewModelScope.launch {
            val req = findMatchingRequirement(vaccination) ?: return@launch
            reminderRepository.markVaccinatedElsewhere(req, source, date, notes, currentUserEmail)
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
