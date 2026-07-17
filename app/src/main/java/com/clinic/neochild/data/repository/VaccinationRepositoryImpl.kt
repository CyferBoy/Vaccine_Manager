package com.clinic.neochild.data.repository

import com.clinic.neochild.data.datasource.vaccination.VaccinationLocalDataSource
import com.clinic.neochild.domain.model.SyncOperation
import com.clinic.neochild.domain.model.SyncPriority
import com.clinic.neochild.domain.model.Vaccination
import com.clinic.neochild.domain.repository.SyncRepository
import com.clinic.neochild.domain.repository.VaccinationRepository
import com.clinic.neochild.utils.AuditLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaccinationRepositoryImpl @Inject constructor(
    private val localDataSource: VaccinationLocalDataSource,
    private val syncRepository: SyncRepository,
    private val auditLogger: AuditLogger
) : VaccinationRepository {

    override val allVaccinations: Flow<List<Vaccination>> = localDataSource.getAllVaccinations()

    override fun getVaccinationsForPatient(patientId: String): Flow<List<Vaccination>> = 
        localDataSource.getVaccinationsForPatient(patientId)

    override suspend fun refreshVaccinations() {
        // Implement remote fetch if needed, but usually triggered via SyncWorker/RefreshUseCase
    }

    override suspend fun addVaccination(vaccination: Vaccination) {
        // 1. Save locally
        localDataSource.insertVaccination(vaccination, isSynced = false)
        
        // 2. Queue for background sync
        syncRepository.enqueue(
            entityName = "VACCINATION",
            entityId = vaccination.id,
            operation = SyncOperation.CREATE,
            priority = SyncPriority.HIGH
        )
        
        auditLogger.logAction("ADD_VACCINATION", vaccination.id, "Patient: ${vaccination.patientId}")
    }

    override suspend fun deleteVaccination(id: String) {
        localDataSource.deleteVaccination(id)
        
        syncRepository.enqueue(
            entityName = "VACCINATION",
            entityId = id,
            operation = SyncOperation.DELETE,
            priority = SyncPriority.MEDIUM
        )
        
        auditLogger.logAction("DELETE_VACCINATION", id)
    }

    override suspend fun markAsDone(id: String) {
        withContext(Dispatchers.IO) {
            val current = localDataSource.getVaccinationById(id)
            if (current != null) {
                val updated = current.copy(isDone = true)
                localDataSource.insertVaccination(updated, isSynced = false)
                
                syncRepository.enqueue(
                    entityName = "VACCINATION",
                    entityId = id,
                    operation = SyncOperation.UPDATE,
                    priority = SyncPriority.MEDIUM
                )
                
                auditLogger.logAction("MARK_DONE_VACCINATION", id)
            }
        }
    }

    override fun getTodayCount(date: String): Flow<Int> = localDataSource.getTodayCount(date)
    override fun getTodayRevenue(date: String): Flow<Double?> = localDataSource.getTodayRevenue(date)
    override fun getTodayCash(date: String): Flow<Double?> = localDataSource.getTodayCash(date)
    override fun getTodayOnline(date: String): Flow<Double?> = localDataSource.getTodayOnline(date)
    override fun getMonthlyCount(pattern: String): Flow<Int> = localDataSource.getMonthlyCount(pattern)
    override fun getMonthlyRevenue(pattern: String): Flow<Double?> = localDataSource.getMonthlyRevenue(pattern)
}
