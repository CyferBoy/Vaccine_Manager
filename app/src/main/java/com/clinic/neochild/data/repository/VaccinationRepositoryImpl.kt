package com.clinic.neochild.data.repository

import com.clinic.neochild.data.datasource.vaccination.VaccinationLocalDataSource
import com.clinic.neochild.data.datasource.vaccination.VaccinationRemoteDataSource
import com.clinic.neochild.data.model.Vaccination
import com.clinic.neochild.domain.repository.VaccinationRepository
import com.clinic.neochild.utils.AuditLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class VaccinationRepositoryImpl @Inject constructor(
    private val localDataSource: VaccinationLocalDataSource,
    private val remoteDataSource: VaccinationRemoteDataSource,
    private val auditLogger: AuditLogger,
    private val repositoryScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : VaccinationRepository {

    override val allVaccinations: Flow<List<Vaccination>> = localDataSource.getAllVaccinations()

    override fun getVaccinationsForPatient(patientId: String): Flow<List<Vaccination>> = 
        localDataSource.getVaccinationsForPatient(patientId)

    override suspend fun refreshVaccinations() {
        withContext(Dispatchers.IO) {
            try {
                val vaccinations = remoteDataSource.fetchAllVaccinations()
                localDataSource.insertVaccinations(vaccinations, isSynced = true)
            } catch (e: Exception) { }
        }
    }

    override suspend fun addVaccination(vaccination: Vaccination) {
        localDataSource.insertVaccination(vaccination, isSynced = false)
        repositoryScope.launch {
            try {
                remoteDataSource.uploadVaccination(vaccination)
                localDataSource.markSynced(vaccination.id)
                auditLogger.logAction("ADD_VACCINATION", vaccination.id, "Patient: ${vaccination.patientId}")
            } catch (e: Exception) { }
        }
    }

    override suspend fun deleteVaccination(id: String) {
        localDataSource.deleteVaccination(id)
        repositoryScope.launch {
            try {
                remoteDataSource.deleteVaccination(id)
                auditLogger.logAction("DELETE_VACCINATION", id)
            } catch (e: Exception) { }
        }
    }

    override suspend fun markAsDone(id: String) {
        withContext(Dispatchers.IO) {
            try {
                // Get current vaccination
                val current = allVaccinations.first().find { it.id == id }
                if (current != null) {
                    val updated = current.copy(isDone = true)
                    localDataSource.insertVaccination(updated, isSynced = false)
                    remoteDataSource.uploadVaccination(updated)
                    localDataSource.markSynced(id)
                }
            } catch (e: Exception) { }
        }
    }
}
