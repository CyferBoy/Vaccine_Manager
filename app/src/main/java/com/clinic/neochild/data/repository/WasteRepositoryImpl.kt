package com.clinic.neochild.data.repository

import androidx.room.withTransaction
import com.clinic.neochild.data.local.database.AppDatabase
import com.clinic.neochild.data.local.entity.toDomain
import com.clinic.neochild.data.local.entity.toEntity
import com.clinic.neochild.domain.model.InventoryTransactionType
import com.clinic.neochild.core.model.SyncOperation
import com.clinic.neochild.core.model.SyncPriority
import com.clinic.neochild.domain.model.WasteRecord
import com.clinic.neochild.domain.repository.InventoryRepository
import com.clinic.neochild.domain.repository.SyncRepository
import com.clinic.neochild.domain.repository.WasteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WasteRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val inventoryRepository: InventoryRepository,
    private val syncRepository: SyncRepository
) : WasteRepository {

    private val wasteDao = database.wasteDao()

    override fun getAllWaste(): Flow<List<WasteRecord>> = 
        wasteDao.getAllWaste().map { list -> list.map { it.toDomain() } }

    override suspend fun recordWaste(record: WasteRecord, user: String) {
        database.withTransaction {
            // 1. Save Locally
            wasteDao.insertWaste(record.toEntity(isSynced = false))

            // 2. Deduct Inventory
            inventoryRepository.deductStock(
                vaccineId = record.vaccineId,
                quantity = record.quantity,
                user = user,
                transactionType = InventoryTransactionType.DAMAGED, // or map from reason
                patientId = null,
                vaccinationId = null
            )

            // 3. Queue Sync
            syncRepository.enqueue(
                entityName = "WASTE",
                entityId = record.id,
                operation = SyncOperation.CREATE,
                priority = SyncPriority.MEDIUM
            )
        }
    }

    override suspend fun deleteWaste(id: String) {
        wasteDao.deleteWaste(id)
        syncRepository.enqueue(
            entityName = "WASTE",
            entityId = id,
            operation = SyncOperation.DELETE,
            priority = SyncPriority.LOW
        )
    }

    override fun getWasteCount(): Flow<Int> = wasteDao.getWasteCount()
}
