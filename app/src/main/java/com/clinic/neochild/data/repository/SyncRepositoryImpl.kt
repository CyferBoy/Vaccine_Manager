package com.clinic.neochild.data.repository

import com.clinic.neochild.data.local.database.AppDatabase
import com.clinic.neochild.data.local.entity.*
import com.clinic.neochild.core.model.SyncItem
import com.clinic.neochild.core.model.SyncOperation
import com.clinic.neochild.core.model.SyncPriority
import com.clinic.neochild.core.model.SyncStatus
import com.clinic.neochild.domain.repository.SyncManager
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
    private val firestore: FirebaseFirestore,
    private val syncManager: SyncManager
) : SyncRepository {

    private val syncDao = database.syncQueueDao()

    override suspend fun enqueue(
        entityName: String,
        entityId: String,
        operation: SyncOperation,
        priority: SyncPriority
    ) {
        if (entityId.isBlank() || entityId == "kotlin.Unit" || entityId == "Unit" || entityId == "null") {
            android.util.Log.e("SyncRepository", "CRITICAL: Attempted to enqueue invalid entityId: $entityId for $entityName")
            return
        }

        syncDao.enqueue(
            SyncQueueEntity(
                entityName = entityName,
                entityId = entityId,
                operation = operation.name,
                priority = priority.name
            )
        )
        syncManager.scheduleSync()
    }

    override fun getPendingCount(): Flow<Int> = syncDao.getPendingCount()

    override fun getSyncQueue(): Flow<List<SyncItem>> = 
        syncDao.getAllItems().map { list -> list.map { it.toDomain() } }

    override suspend fun clearSyncedItems() {
        syncDao.clearSynced()
    }

    override suspend fun processNextItems() {
        syncDao.cleanCorruptedItems()

        val pending = syncDao.getItemsByStatus(SyncStatus.PENDING.name)
        if (pending.isEmpty()) return

        var hasRetryableError = false

        for (item in pending) {
            try {
                syncDao.updateStatus(item.queueId, SyncStatus.SYNCING.name)
                uploadEntity(item)
                syncDao.updateStatus(item.queueId, SyncStatus.SYNCED.name)
                syncDao.deleteItem(item) 
            } catch (e: Exception) {
                val isNetworkError = e is java.io.IOException || e.message?.contains("network", ignoreCase = true) == true
                
                if (isNetworkError && item.retryCount < 5) {
                    syncDao.incrementRetryCount(item.queueId, e.message ?: "Network error")
                    syncDao.updateStatus(item.queueId, SyncStatus.PENDING.name)
                    hasRetryableError = true
                } else {
                    syncDao.markFailed(item.queueId, SyncStatus.FAILED.name, e.message ?: "Sync failed")
                }
            }
        }
        
        if (hasRetryableError) {
            throw java.io.IOException("Some items failed due to network and need retry")
        }
    }

    private suspend fun uploadEntity(item: SyncQueueEntity) {
        val collection = when (item.entityName) {
            "PATIENT" -> "patients"
            "VACCINATION" -> "vaccinations"
            "WASTE" -> "waste"
            "DUE_REMINDER" -> "due_reminders"
            "COMPLETED_REMINDER" -> "completed_reminders"
            "DISMISSED_REMINDER" -> "dismissed_reminders"
            "EXTERNAL_REMINDER" -> "external_reminders"
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
            "DUE_REMINDER" -> database.dueReminderDao().getDueReminderById(item.entityId.toLong())
            "COMPLETED_REMINDER" -> database.dueReminderDao().getCompletedReminderById(item.entityId.toLong())
            "DISMISSED_REMINDER" -> database.dueReminderDao().getDismissedReminderById(item.entityId.toLong())
            "EXTERNAL_REMINDER" -> database.dueReminderDao().getExternalReminderById(item.entityId.toLong())
            "REMINDER_OVERRIDE" -> database.reminderDao().getReminderById(item.entityId.toLong())
            "REMINDER_AUDIT" -> database.reminderAuditDao().getAuditById(item.entityId.toLong())
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
        syncManager.scheduleSync()
    }

    override suspend fun deleteQueueItem(queueId: Long) {
        val item = syncDao.getItemById(queueId)
        if (item != null) {
            syncDao.deleteItem(item)
        }
    }

    override suspend fun retryItem(queueId: Long) {
        syncDao.updateStatus(queueId, SyncStatus.PENDING.name)
        syncManager.scheduleSync()
    }

    override suspend fun deleteAllFailed() {
        val failed = syncDao.getItemsByStatus(SyncStatus.FAILED.name)
        for (item in failed) {
            syncDao.deleteItem(item)
        }
    }
}
