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
                } else {
                    syncDao.markFailed(item.queueId, SyncStatus.FAILED.name, e.message ?: "Sync failed")
                }
            }
        }
    }

    private suspend fun uploadEntity(item: SyncQueueEntity) {
        val collection = when (item.entityName) {
            "PATIENT" -> "patients"
            "VISIT", "VACCINATION" -> "visits"
            "WASTE" -> "waste"
            "REMINDER_STATE", "DUE_REMINDER", "COMPLETED_REMINDER", "DISMISSED_REMINDER", "EXTERNAL_REMINDER" -> "reminders"
            "VACCINE" -> "vaccines"
            "BATCH" -> "batches"
            "TRANSACTION" -> "transactions"
            "PATIENT_NOTE" -> "patient_notes"
            "FINANCE" -> "finance"
            "STAFF" -> "staff"
            "USER" -> "users"
            "BORROW" -> "borrow"
            "AUDIT_LOG" -> "audit_logs"
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
        val entityId = item.entityId
        
        // Reminder State stable ID handling
        if (item.entityName == "REMINDER_STATE" && entityId.contains("||")) {
            val parts = entityId.split("||")
            if (parts.size == 3) {
                return database.dueReminderDao().getReminderByStableId(parts[0], parts[1], parts[2])
            }
        }

        return try {
            when (item.entityName) {
                "PATIENT" -> database.patientDao().getPatientById(entityId)?.toPatient()
                "VISIT", "VACCINATION" -> database.vaccinationDao().getVaccinationById(entityId)?.toVaccination()
                "WASTE" -> database.wasteDao().getWasteById(entityId)?.toDomain()
                "REMINDER_STATE" -> database.dueReminderDao().getReminderById(entityId.toLongOrNull() ?: -1L)
                "VACCINE" -> database.vaccineDao().getVaccineById(entityId)
                "BATCH" -> database.vaccineDao().getBatchById(entityId)
                "TRANSACTION" -> database.vaccineDao().getTransactionById(entityId.toLongOrNull() ?: -1L)
                "FINANCE" -> database.financeDao().getUnsyncedTransactions().find { it.id.toString() == entityId } // Optimization needed
                "AUDIT_LOG" -> database.auditLogDao().getUnsyncedLogs().find { it.id.toString() == entityId }
                else -> null
            }
        } catch (e: Exception) {
            null
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
