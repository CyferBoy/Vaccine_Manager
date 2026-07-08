package com.clinic.neochild.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clinic.neochild.data.local.AppDatabase
import com.clinic.neochild.data.model.Patient
import com.clinic.neochild.data.model.Vaccination
import com.clinic.neochild.data.repository.PatientRepository
import com.clinic.neochild.data.repository.VaccinationRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class PatientViewModel(application: Application) : AndroidViewModel(application) {
    private val patientRepository: PatientRepository
    private val vaccinationRepository: VaccinationRepository
    
    val allPatients: StateFlow<List<Patient>>
    val allVaccinations: StateFlow<List<Vaccination>>
    val patientsWithMissingPrice: StateFlow<Set<String>>

    init {
        val database = AppDatabase.getDatabase(application)
        patientRepository = PatientRepository(database.patientDao())
        vaccinationRepository = VaccinationRepository(database.vaccinationDao())
        
        allPatients = patientRepository.allPatients.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

        allVaccinations = vaccinationRepository.allVaccinations.stateIn(
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
            patientRepository.refreshPatients()
            vaccinationRepository.refreshVaccinations()
        }
    }

    fun deletePatient(id: String) {
        viewModelScope.launch {
            patientRepository.deletePatient(id)
        }
    }

    fun savePatient(patient: Patient, onComplete: () -> Unit) {
        viewModelScope.launch {
            patientRepository.addPatient(patient)
            onComplete()
        }
    }

    fun deleteVaccination(id: String) {
        viewModelScope.launch {
            vaccinationRepository.deleteVaccination(id)
        }
    }

    fun saveVaccination(vaccination: Vaccination, onComplete: () -> Unit) {
        viewModelScope.launch {
            vaccinationRepository.addVaccination(vaccination)
            onComplete()
        }
    }
}
