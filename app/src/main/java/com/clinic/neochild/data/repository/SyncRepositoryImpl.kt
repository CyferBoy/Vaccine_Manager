package com.clinic.neochild.data.repository

import com.clinic.neochild.data.local.AppDatabase
import com.clinic.neochild.data.local.entity.SyncQueueEntity
import com.clinic.neochild.data.local.entity.toDomain
import com.clinic.neochild.data.local.entity.toPatient
import com.clinic.neochild.data.local.entity.toVaccination
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
                syncDao.deleteItem(item) // Optionally keep history or delete
            } catch (e: Exception) {
                syncDao.markFailed(item.queueId, SyncStatus.FAILED.name, e.message ?: "Unknown error")
            }
        }
    }

    private suspend fun uploadEntity(item: SyncQueueEntity) {
        val collection = when (item.entityName) {
            "PATIENT" -> "patients"
            "VACCINATION" -> "vaccinations"
            "INVENTORY" -> "inventory"
            "BATCH" -> "vaccine_batches"
            "TRANSACTION" -> "inventory_transactions"
            else -> throw IllegalArgumentException("Unknown entity: ${item.entityName}")
        }

        val docRef = firestore.collection(collection).document(item.entityId)

        if (item.operation == SyncOperation.DELETE.name) {
            docRef.delete().await()
            return
        }

        val data = when (item.entityName) {
            "PATIENT" -> database.patientDao().getPatientById(item.entityId)?.toPatient()
            "VACCINATION" -> database.vaccinationDao().getVaccinationById(item.entityId)?.toVaccination()
            // Add other mappers
            else -> null
        }

        if (data != null) {
            docRef.set(data).await()
        }
    }

    override suspend fun retryFailedItems() {
        val failed = syncDao.getItemsByStatus(SyncStatus.FAILED.name)
        for (item in failed) {
            syncDao.updateStatus(item.queueId, SyncStatus.PENDING.name)
        }
    }
}
