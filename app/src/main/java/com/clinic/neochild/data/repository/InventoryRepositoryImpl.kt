package com.clinic.neochild.data.repository

import androidx.room.withTransaction
import com.clinic.neochild.data.local.AppDatabase
import com.clinic.neochild.data.local.entity.InventoryTransactionEntity
import com.clinic.neochild.data.local.entity.VaccineBatchEntity
import com.clinic.neochild.data.local.entity.VaccineEntity
import com.clinic.neochild.data.model.InventoryTransactionType
import com.clinic.neochild.domain.repository.InventoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InventoryRepositoryImpl @Inject constructor(
    private val database: AppDatabase
) : InventoryRepository {

    private val vaccineDao = database.vaccineDao()

    override fun getInventoryItems(): Flow<List<com.clinic.neochild.domain.model.InventoryItem>> {
        return combine(
            vaccineDao.getAllVaccines(),
            vaccineDao.getAllBatches()
        ) { definitions, batches ->
            definitions.map { def ->
                val stock = batches.filter { it.vaccineId == def.id && !it.isDeleted }
                    .sumOf { it.remainingQuantity }
                com.clinic.neochild.domain.model.InventoryItem(
                    id = def.id,
                    brandName = def.brandName,
                    stock = stock,
                    threshold = def.lowStockThreshold,
                    type = def.type,
                    company = def.companyName
                )
            }
        }
    }

    override fun getAllVaccines(): Flow<List<VaccineEntity>> = vaccineDao.getAllVaccines()

    override fun getVaccineBatches(vaccineId: String): Flow<List<VaccineBatchEntity>> = 
        // Need to add this specific query or use filter
        vaccineDao.getAllBatches() // Simplified for now, should be filtered by vaccineId

    override fun getInventoryTransactions(vaccineId: String): Flow<List<InventoryTransactionEntity>> = 
        vaccineDao.getTransactionsForVaccine(vaccineId)

    override suspend fun addVaccineDefinition(vaccine: VaccineEntity) {
        vaccineDao.insertVaccine(vaccine)
    }

    override suspend fun addBatch(batch: VaccineBatchEntity, user: String) {
        database.withTransaction {
            val currentTotal = vaccineDao.getTotalStockForVaccine(batch.vaccineId) ?: 0
            vaccineDao.insertBatch(batch)
            
            vaccineDao.insertTransaction(
                InventoryTransactionEntity(
                    vaccineId = batch.vaccineId,
                    batchId = batch.batchId,
                    transactionType = InventoryTransactionType.PURCHASE.name,
                    quantity = batch.purchaseQuantity,
                    previousQuantity = currentTotal,
                    currentQuantity = currentTotal + batch.purchaseQuantity,
                    user = user,
                    notes = "New Batch: ${batch.batchNumber}"
                )
            )
        }
    }

    override suspend fun deductStock(
        vaccineId: String,
        quantity: Int,
        user: String,
        transactionType: InventoryTransactionType,
        vaccinationId: String?,
        patientId: String?
    ) {
        database.withTransaction {
            var remainingToDeduct = quantity
            val activeBatches = vaccineDao.getActiveBatchesByExpiry(vaccineId)

            for (batch in activeBatches) {
                if (remainingToDeduct <= 0) break

                val deductFromThisBatch = minOf(batch.remainingQuantity, remainingToDeduct)
                val prevQty = vaccineDao.getTotalStockForVaccine(vaccineId) ?: 0
                
                val updatedBatch = batch.copy(
                    remainingQuantity = batch.remainingQuantity - deductFromThisBatch
                )
                vaccineDao.updateBatch(updatedBatch)

                vaccineDao.insertTransaction(
                    InventoryTransactionEntity(
                        vaccineId = vaccineId,
                        batchId = batch.batchId,
                        patientId = patientId,
                        vaccinationId = vaccinationId,
                        transactionType = transactionType.name,
                        quantity = -deductFromThisBatch,
                        previousQuantity = prevQty,
                        currentQuantity = prevQty - deductFromThisBatch,
                        user = user
                    )
                )

                remainingToDeduct -= deductFromThisBatch
            }

            if (remainingToDeduct > 0) {
                throw IllegalStateException("Insufficient stock for vaccine $vaccineId")
            }
        }
    }

    override suspend fun adjustStock(batchId: String, newQuantity: Int, user: String, reason: String) {
        // Implementation for manual adjustment
    }
}
