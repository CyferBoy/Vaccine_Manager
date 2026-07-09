package com.clinic.neochild.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinic.neochild.data.model.Patient
import com.clinic.neochild.data.model.Vaccination
import com.clinic.neochild.domain.usecase.patient.GetPatientsUseCase
import com.clinic.neochild.domain.usecase.vaccination.GetVaccinationsUseCase
import com.clinic.neochild.domain.usecase.vaccination.SaveVaccinationUseCase
import com.clinic.neochild.utils.PatientUtils
import com.clinic.neochild.utils.ReminderEngine
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
    private val saveVaccinationUseCase: SaveVaccinationUseCase
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
        
        // Convert requirements back to Vaccination objects for the UI to display
        // Map unsatisfied requirements to their original vaccination records
        val pending = unsatisfied.mapNotNull { req -> 
            vaccinations.find { it.id == req.originalVisitId }?.copy(
                nxtVaccineNames = listOf(req.vaccineName),
                nextDueDate = PatientUtils.formatDate(req.dueDate)
            )
        }.distinctBy { it.patientId + it.nextDueDate + it.nxtVaccineNames.joinToString() }

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
            saveVaccinationUseCase(vaccination.copy(isDone = true))
        }
    }
}
