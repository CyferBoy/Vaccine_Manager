package com.clinic.neochild.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinic.neochild.domain.model.Patient
import com.clinic.neochild.domain.model.Vaccination
import com.clinic.neochild.domain.repository.ReminderRepository
import com.clinic.neochild.domain.repository.VaccinationRepository
import com.clinic.neochild.domain.usecase.patient.DeletePatientUseCase
import com.clinic.neochild.domain.usecase.patient.GetPatientByIdUseCase
import com.clinic.neochild.domain.usecase.patient.GetPatientsUseCase
import com.clinic.neochild.domain.usecase.patient.SavePatientUseCase
import com.clinic.neochild.domain.usecase.sync.RefreshDataUseCase
import com.clinic.neochild.domain.usecase.vaccination.DeleteVaccinationUseCase
import com.clinic.neochild.domain.usecase.vaccination.GetVaccinationsUseCase
import com.clinic.neochild.domain.usecase.vaccination.SaveVaccinationUseCase
import com.clinic.neochild.domain.logic.ReminderEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PatientViewModel @Inject constructor(
    private val getPatientsUseCase: GetPatientsUseCase,
    private val getPatientByIdUseCase: GetPatientByIdUseCase,
    private val getVaccinationsUseCase: GetVaccinationsUseCase,
    private val savePatientUseCase: SavePatientUseCase,
    private val deletePatientUseCase: DeletePatientUseCase,
    private val saveVaccinationUseCase: SaveVaccinationUseCase,
    private val deleteVaccinationUseCase: DeleteVaccinationUseCase,
    private val refreshDataUseCase: RefreshDataUseCase,
    private val vaccinationRepository: VaccinationRepository,
    private val reminderRepository: ReminderRepository
) : ViewModel() {
    
    val allPatients: StateFlow<List<Patient>>
    val allVaccinations: StateFlow<List<Vaccination>>
    val patientsWithMissingPrice: StateFlow<Set<String>>

    init {
        // State Streams
        allPatients = getPatientsUseCase().stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

        allVaccinations = getVaccinationsUseCase().stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

        patientsWithMissingPrice = allVaccinations.map { vaccinations ->
            vaccinations.filter { it.cost <= 0.0 }.map { it.patientId }.toSet()
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            refreshDataUseCase()
        }
    }

    fun deletePatient(id: String) {
        viewModelScope.launch {
            deletePatientUseCase(id)
        }
    }

    suspend fun getPatientById(id: String): Patient? {
        return getPatientByIdUseCase(id)
    }

    fun savePatient(patient: Patient, onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                savePatientUseCase(patient)
                onComplete()
            } catch (e: Exception) {
                // Handle validation or save error
            }
        }
    }

    fun deleteVaccination(id: String) {
        viewModelScope.launch {
            deleteVaccinationUseCase(id)
        }
    }

    fun saveVaccination(vaccination: Vaccination, onComplete: () -> Unit) {
        viewModelScope.launch {
            saveVaccinationUseCase(vaccination)
            onComplete()
        }
    }

    fun markAsDone(vaccination: Vaccination) {
        viewModelScope.launch {
            // 1. Mark the vaccination record itself as done
            vaccinationRepository.markAsDone(vaccination.id)
            
            // 2. Clear associated reminders
            val allRequirements = ReminderEngine.getPotentialRequirements(allVaccinations.value)
            val matching = allRequirements.filter { req ->
                req.patientId == vaccination.patientId && 
                vaccination.nxtVaccineNames.contains(req.vaccineName)
            }
            
            for (req in matching) {
                reminderRepository.markAsDone(req)
            }
        }
    }
}
