package com.clinic.neochild.data.repository

import com.clinic.neochild.data.local.database.AppDatabase
import com.clinic.neochild.data.local.entity.toVaccination
import com.clinic.neochild.data.local.entity.toEntity
import com.clinic.neochild.core.model.SyncOperation
import com.clinic.neochild.core.model.SyncPriority
import com.clinic.neochild.core.logger.AuditLogger
import com.clinic.neochild.domain.model.Vaccination
import com.clinic.neochild.domain.repository.SyncRepository
import com.clinic.neochild.domain.repository.VaccinationRepository
import com.clinic.neochild.data.remote.mapper.FirestoreMappers
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaccinationRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val firestore: FirebaseFirestore,
    private val syncRepository: SyncRepository,
    private val auditLogger: AuditLogger
) : VaccinationRepository {

    private val vaccinationDao = database.vaccinationDao()

    override val allVaccinations: Flow<List<Vaccination>> = 
        vaccinationDao.getAllVaccinations().map { list -> list.map { it.toVaccination() } }

    override fun getVaccinationsForPatient(patientId: String): Flow<List<Vaccination>> = 
        vaccinationDao.getVaccinationsForPatient(patientId).map { list -> list.map { it.toVaccination() } }

    override suspend fun refreshVaccinations() {
        withContext(Dispatchers.IO) {
            try {
                val snapshot = firestore.collection("vaccinations").get().await()
                val vaccinations = snapshot.documents.mapNotNull { FirestoreMappers.toVaccination(it) }
                vaccinationDao.insertVaccinations(vaccinations.map { it.toEntity() })
            } catch (e: Exception) {
                // Error handling
            }
        }
    }

    override suspend fun addVaccination(vaccination: Vaccination) {
        // 1. Check if it's an update
        val existing = vaccinationDao.getVaccinationById(vaccination.id)
        
        // 2. Save locally
        vaccinationDao.insertVaccination(vaccination.toEntity(isSynced = false))
        
        // 3. Queue for background sync
        val operation = if (existing == null) SyncOperation.CREATE else SyncOperation.UPDATE
        
        syncRepository.enqueue(
            entityName = "VACCINATION",
            entityId = vaccination.id,
            operation = operation,
            priority = SyncPriority.HIGH
        )
        
        val auditAction = if (existing == null) "ADD_VACCINATION" else "UPDATE_VACCINATION"
        auditLogger.logAction(auditAction, vaccination.id, "Patient: ${vaccination.patientId}")
    }

    override suspend fun deleteVaccination(id: String) {
        vaccinationDao.deleteVaccination(id)
        
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
            val current = vaccinationDao.getVaccinationById(id)
            if (current != null) {
                val updated = current.copy(isDone = true, isSynced = false)
                vaccinationDao.insertVaccination(updated)
                
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

    override fun getTodayCount(date: String): Flow<Int> = vaccinationDao.getCountByDate(date)
    override fun getTodayRevenue(date: String): Flow<Double?> = vaccinationDao.getRevenueByDate(date)
    override fun getTodayCash(date: String): Flow<Double?> = vaccinationDao.getCashByDate(date)
    override fun getTodayOnline(date: String): Flow<Double?> = vaccinationDao.getOnlineByDate(date)
    override fun getMonthlyCount(pattern: String): Flow<Int> = vaccinationDao.getMonthlyCount(pattern)
    override fun getMonthlyRevenue(pattern: String): Flow<Double?> = vaccinationDao.getMonthlyRevenue(pattern)
    override fun getVaccineNamesForMonth(pattern: String): Flow<List<String>> = vaccinationDao.getVaccineNamesForMonth(pattern)

    override suspend fun transferVaccinations(duplicateId: String, masterId: String) {
        vaccinationDao.updatePatientId(duplicateId, masterId)
    }
}

