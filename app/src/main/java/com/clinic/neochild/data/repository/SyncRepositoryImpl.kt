package com.clinic.neochild.data.repository

import com.clinic.neochild.data.local.AppDatabase
import com.clinic.neochild.data.local.entity.*
import com.clinic.neochild.domain.model.SyncItem
import com.clinic.neochild.domain.model.SyncOperation
import com.clinic.neochild.domain.model.SyncPriority
import com.clinic.neochild.domain.model.SyncStatus
import com.clinic.neochild.domain.repository.SyncRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val firestore: FirebaseFirestore
) : SyncRepository {

    private val syncDao = database.syncQueueDao()

    override suspend fun enqueue(
        entityName: String,
        entityId: String,
        operation: SyncOperation,
        priority: SyncPriority
    ) {
        syncDao.enqueue(
            SyncQueueEntity(
                entityName = entityName,
                entityId = entityId,
                operation = operation.name,
                priority = priority.name
            )
        )
    }

    override fun getPendingCount(): Flow<Int> = syncDao.getPendingCount()

    override fun getSyncQueue(): Flow<List<SyncItem>> = 
        syncDao.getAllItems().map { list -> list.map { it.toDomain() } }

    override suspend fun clearSyncedItems() {
        syncDao.clearSynced()
    }

    override suspend fun processNextItems() {
        val pending = syncDao.getItemsByStatus(SyncStatus.PENDING.name)
        if (pending.isEmpty()) return

        for (item in pending) {
            try {
                syncDao.updateStatus(item.queueId, SyncStatus.SYNCING.name)
                uploadEntity(item)
                syncDao.updateStatus(item.queueId, SyncStatus.SYNCED.name)
                syncDao.deleteItem(item) 
            } catch (e: Exception) {
                syncDao.markFailed(item.queueId, SyncStatus.FAILED.name, e.message ?: "Unknown error")
            }
        }
    }

    private suspend fun uploadEntity(item: SyncQueueEntity) {
        val collection = when (item.entityName) {
            "PATIENT" -> "patients"
            "VACCINATION" -> "vaccinations"
            "WASTE" -> "waste"
            "REMINDER_OVERRIDE" -> "reminder_overrides"
            "REMINDER_AUDIT" -> "reminder_audits"
            "VACCINE" -> "vaccines"
            "BATCH" -> "vaccine_batches"
            "TRANSACTION" -> "inventory_transactions"
            else -> throw IllegalArgumentException("Unknown entity: ${item.entityName}")
        }

        val docRef = firestore.collection(collection).document(item.entityId)

        if (item.operation == SyncOperation.DELETE.name) {
            docRef.delete().await()
            return
        }

        val finalData = fetchEntityData(item)

        if (finalData != null) {
            docRef.set(finalData).await()
        }
    }
    
    private suspend fun fetchEntityData(item: SyncQueueEntity): Any? {
        return when (item.entityName) {
            "PATIENT" -> database.patientDao().getPatientById(item.entityId)?.toPatient()
            "VACCINATION" -> database.vaccinationDao().getVaccinationById(item.entityId)?.toVaccination()
            "WASTE" -> database.wasteDao().getWasteById(item.entityId)?.toDomain()
            "REMINDER_OVERRIDE" -> database.reminderDao().getReminderById(item.entityId.toLong())
            "REMINDER_AUDIT" -> database.reminderAuditDao().getUnsyncedAudits().find { it.auditId.toString() == item.entityId }
            "VACCINE" -> database.vaccineDao().getVaccineById(item.entityId)
            "BATCH" -> database.vaccineDao().getBatchById(item.entityId)
            "TRANSACTION" -> database.vaccineDao().getTransactionById(item.entityId.toLong())
            else -> null
        }
    }

    override suspend fun retryFailedItems() {
        val failed = syncDao.getItemsByStatus(SyncStatus.FAILED.name)
        for (item in failed) {
            syncDao.updateStatus(item.queueId, SyncStatus.PENDING.name)
        }
    }
}
