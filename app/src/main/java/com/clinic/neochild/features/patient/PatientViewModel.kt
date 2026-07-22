package com.clinic.neochild.features.patient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinic.neochild.data.local.entity.AuditLogEntity
import com.clinic.neochild.domain.model.Patient
import com.clinic.neochild.domain.model.Vaccination
import com.clinic.neochild.domain.repository.PatientRepository
import com.clinic.neochild.domain.usecase.patient.DeletePatientUseCase
import com.clinic.neochild.domain.usecase.patient.GetPatientByIdUseCase
import com.clinic.neochild.domain.usecase.patient.GetPatientsUseCase
import com.clinic.neochild.domain.usecase.patient.SavePatientUseCase
import com.clinic.neochild.domain.usecase.sync.RefreshDataUseCase
import com.clinic.neochild.domain.usecase.vaccination.CompleteVaccinationUseCase
import com.clinic.neochild.domain.usecase.vaccination.DeleteVaccinationUseCase
import com.clinic.neochild.domain.usecase.vaccination.GetVaccinationsUseCase
import com.clinic.neochild.domain.usecase.vaccination.SaveVaccinationUseCase
import com.google.firebase.auth.FirebaseAuth
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
    private val completeVaccinationUseCase: CompleteVaccinationUseCase,
    private val refreshDataUseCase: RefreshDataUseCase,
    private val patientRepository: PatientRepository
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
            val user = FirebaseAuth.getInstance().currentUser?.email ?: "Unknown"
            completeVaccinationUseCase.satisfyExisting(vaccination.id, user)
        }
    }

    fun getAuditLogs(patientId: String): Flow<List<AuditLogEntity>> {
        return patientRepository.getPatientTimeline(patientId)
    }
}
