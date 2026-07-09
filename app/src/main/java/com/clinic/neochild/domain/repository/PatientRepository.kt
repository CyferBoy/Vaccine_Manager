package com.clinic.neochild.domain.repository

import com.clinic.neochild.data.model.Patient
import kotlinx.coroutines.flow.Flow

/**
 * Domain-level Repository interface for Patient data.
 * This is the single source of truth contract for the UI and Use Cases.
 */
interface PatientRepository {
    val allPatients: Flow<List<Patient>>
    suspend fun getPatientById(id: String): Patient?
    suspend fun refreshPatients()
    suspend fun addPatient(patient: Patient)
    suspend fun deletePatient(id: String)
    suspend fun mergePatients(masterId: String, duplicateIds: List<String>)
}
