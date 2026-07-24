package com.clinic.neochild.data.repository

import com.clinic.neochild.data.local.database.AppDatabase
import androidx.room.withTransaction
import com.clinic.neochild.data.local.dao.*
import com.clinic.neochild.data.local.entity.*
import com.clinic.neochild.domain.model.Vaccine
import com.clinic.neochild.domain.repository.VaccineRepository
import com.clinic.neochild.domain.repository.SyncRepository
import com.clinic.neochild.core.model.SyncOperation
import com.clinic.neochild.core.model.SyncPriority
import com.clinic.neochild.core.logger.AuditLogger
import com.clinic.neochild.data.remote.mapper.FirestoreMappers
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaccineRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val firestore: FirebaseFirestore,
    private val syncRepository: SyncRepository,
    private val auditLogger: AuditLogger
) : VaccineRepository {

    private val vaccineDao = database.vaccineDao()
    private val wasteDao = database.wasteDao()
    private val borrowDao = database.borrowDao()
    private val auditLogDao = database.auditLogDao()
    private val syncQueueDao = database.syncQueueDao()

    override fun getInventory(): Flow<List<Vaccine>> {
        return combine(
            vaccineDao.getAllVaccines(),
            vaccineDao.getAllBatches()
        ) { vaccines, batches ->
            val batchMap = batches.groupBy { it.vaccineId }
            vaccines.map { entity ->
                val totalStock = batchMap[entity.id]?.sumOf { it.remainingQuantity } ?: 0
                entity.toVaccine(totalStock)
            }
        }
    }

    override suspend fun refreshInventory() {
        withContext(Dispatchers.IO) {
            try {
                // 1. Refresh Vaccines
                val snap = firestore.collection("vaccines").get().await()
                val vaccines = snap.documents.mapNotNull { FirestoreMappers.toVaccineEntity(it) }
                
                // 2. Refresh Batches
                val batchSnap = firestore.collection("vaccine_batches").get().await()
                val batches = batchSnap.documents.mapNotNull { FirestoreMappers.toVaccineBatchEntity(it) }

                database.withTransaction {
                    for (v in vaccines) {
                        if (!syncQueueDao.isUnsynced("VACCINE", v.id)) {
                            vaccineDao.insertVaccine(v)
                        }
                    }
                    for (b in batches) {
                        if (!syncQueueDao.isUnsynced("BATCH", b.batchId)) {
                            vaccineDao.insertBatch(b)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("VaccineRepo", "Refresh failed", e)
            }
        }
    }

    override suspend fun deleteVaccine(id: String) {
        vaccineDao.deleteVaccine(id)
        syncRepository.enqueue("VACCINE", id, SyncOperation.DELETE, SyncPriority.MEDIUM)
        
        auditLogger.log(
            module = "VACCINE",
            entityType = "VACCINE",
            entityId = id,
            action = "DELETED"
        )
    }

    override fun getBatches(vaccineId: String): Flow<List<VaccineBatchEntity>> {
        return vaccineDao.getBatchesForVaccine(vaccineId)
    }

    override suspend fun addBatch(batch: VaccineBatchEntity) {
        vaccineDao.insertBatch(batch)
        syncRepository.enqueue("BATCH", batch.batchId, SyncOperation.CREATE, SyncPriority.HIGH)
        
        auditLogger.log(
            module = "VACCINE",
            entityType = "BATCH",
            entityId = batch.batchId,
            action = "CREATED",
            remarks = "Batch ${batch.batchNumber} added for ${batch.vaccineId}"
        )
    }

    override fun getWasteRecords(vaccineId: String): Flow<List<WasteEntity>> {
        return wasteDao.getWasteForVaccine(vaccineId)
    }

    override fun getBorrowRecords(vaccineId: String): Flow<List<BorrowEntity>> {
        return borrowDao.getRecordsForVaccine(vaccineId)
    }

    override fun getVaccineTimeline(vaccineId: String): Flow<List<AuditLogEntity>> {
        return auditLogDao.getLogsForEntity("VACCINE", vaccineId)
    }

    override suspend fun refreshBorrowRecords() {
        withContext(Dispatchers.IO) {
            try {
                val snapshot = firestore.collection("borrow").get().await()
                val records = snapshot.documents.mapNotNull { FirestoreMappers.toBorrowEntity(it) }
                database.withTransaction {
                    for (remote in records) {
                        // Borrow records usually use UUIDs as PK, so we can just insert
                        borrowDao.insertRecord(remote.copy(isSynced = true))
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("VaccineRepo", "Borrow refresh failed", e)
            }
        }
    }
}
