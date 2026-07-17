package com.clinic.neochild.features.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinic.neochild.domain.model.Patient
import com.clinic.neochild.domain.model.Vaccination
import com.clinic.neochild.domain.model.TimelineEvent
import com.clinic.neochild.domain.usecase.patient.GetPatientTimelineUseCase
import com.clinic.neochild.domain.usecase.patient.GetPatientsUseCase
import com.clinic.neochild.domain.usecase.vaccination.GetVaccinationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class TimelineUiState(
    val patient: Patient? = null,
    val events: List<TimelineEvent> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val getPatientsUseCase: GetPatientsUseCase,
    private val getVaccinationsUseCase: GetVaccinationsUseCase,
    private val getTimelineUseCase: GetPatientTimelineUseCase
) : ViewModel() {

    fun getTimeline(patientId: String): StateFlow<TimelineUiState> {
        return combine(
            getPatientsUseCase(),
            getVaccinationsUseCase()
        ) { patients, vaccinations ->
            val patient = patients.find { it.id == patientId }
            val patientVaccinations = vaccinations.filter { it.patientId == patientId }
            
            if (patient != null) {
                val timelineEvents = getTimelineUseCase(patient, patientVaccinations).first()
                TimelineUiState(patient, timelineEvents, false)
            } else {
                TimelineUiState(isLoading = false)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimelineUiState(isLoading = true))
    }
}
