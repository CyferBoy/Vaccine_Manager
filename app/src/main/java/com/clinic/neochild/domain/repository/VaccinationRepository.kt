package com.clinic.neochild.domain.repository

import com.clinic.neochild.domain.model.Vaccination
import kotlinx.coroutines.flow.Flow

interface VaccinationRepository {
    val allVaccinations: Flow<List<Vaccination>>
    fun getVaccinationsForPatient(patientId: String): Flow<List<Vaccination>>
    suspend fun refreshVaccinations()
    suspend fun addVaccination(vaccination: Vaccination)
    suspend fun deleteVaccination(id: String)
    suspend fun markAsDone(id: String)
}
