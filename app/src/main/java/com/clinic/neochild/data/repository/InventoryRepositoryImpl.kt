package com.clinic.neochild.data.repository

import androidx.room.withTransaction
import com.clinic.neochild.data.local.database.AppDatabase
import com.clinic.neochild.data.local.entity.InventoryTransactionEntity
import com.clinic.neochild.data.local.entity.VaccineBatchEntity
import com.clinic.neochild.data.local.entity.VaccineEntity
import com.clinic.neochild.data.remote.mapper.FirestoreMappers
import com.google.firebase.firestore.FirebaseFirestore
import com.clinic.neochild.domain.model.InventoryTransactionType
import com.clinic.neochild.core.model.SyncOperation
import com.clinic.neochild.core.model.SyncPriority
import com.clinic.neochild.domain.repository.InventoryRepository
import com.clinic.neochild.domain.repository.SyncRepository
import com.clinic.neochild.core.utils.InventoryUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InventoryRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val firestore: FirebaseFirestore,
    private val syncRepository: SyncRepository,
    private val auditLogger: com.clinic.neochild.core.logger.AuditLogger
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

    override fun getAllBatches(): Flow<List<VaccineBatchEntity>> = vaccineDao.getAllBatches()

    override fun getVaccineBatches(vaccineId: String): Flow<List<VaccineBatchEntity>> = 
        vaccineDao.getBatchesByVaccine(vaccineId)

    override fun getInventoryTransactions(vaccineId: String): Flow<List<InventoryTransactionEntity>> = 
        vaccineDao.getTransactionsForVaccine(vaccineId)

    override suspend fun addVaccineDefinition(vaccine: VaccineEntity) {
        vaccineDao.insertVaccine(vaccine)
        syncRepository.enqueue("VACCINE", vaccine.id, SyncOperation.CREATE, SyncPriority.MEDIUM)
    }

    override suspend fun addBatch(batch: VaccineBatchEntity, user: String) {
        database.withTransaction {
            val currentTotal = vaccineDao.getTotalStockForVaccine(batch.vaccineId) ?: 0
            vaccineDao.insertBatch(batch)
            
            val transaction = InventoryTransactionEntity(
                vaccineId = batch.vaccineId,
                batchId = batch.batchId,
                transactionType = InventoryTransactionType.PURCHASE.name,
                quantity = batch.purchaseQuantity,
                previousQuantity = currentTotal,
                currentQuantity = currentTotal + batch.purchaseQuantity,
                user = user,
                notes = "New Batch: ${batch.batchNumber}"
            )
            vaccineDao.insertTransaction(transaction)
            
            // Queue Sync
            syncRepository.enqueue("BATCH", batch.batchId, SyncOperation.CREATE, SyncPriority.MEDIUM)
            // Transactions could also be synced if needed for remote audit
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
            val allActiveBatches = vaccineDao.getActiveBatchesByExpiry(vaccineId)
            
            // Filter out expired batches for administration
            val validBatches = allActiveBatches.filter { !InventoryUtils.isExpired(it.expiryDate) }

            for (batch in validBatches) {
                if (remainingToDeduct <= 0) break

                val deductFromThisBatch = minOf(batch.remainingQuantity, remainingToDeduct)
                val prevQty = vaccineDao.getTotalStockForVaccine(vaccineId) ?: 0
                
                val updatedBatch = batch.copy(
                    remainingQuantity = batch.remainingQuantity - deductFromThisBatch
                )
                vaccineDao.updateBatch(updatedBatch)

                val transaction = InventoryTransactionEntity(
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
                vaccineDao.insertTransaction(transaction)
                
                // Queue Sync for the updated batch
                syncRepository.enqueue("BATCH", batch.batchId, SyncOperation.UPDATE, SyncPriority.HIGH)

                remainingToDeduct -= deductFromThisBatch
            }

            if (remainingToDeduct > 0) {
                val expiredCount = allActiveBatches.size - validBatches.size
                if (expiredCount > 0 && validBatches.isEmpty()) {
                    throw IllegalStateException("All available batches for this vaccine have expired. Cannot proceed with vaccination.")
                } else {
                    throw IllegalStateException("Insufficient non-expired stock for vaccine $vaccineId. Available: ${quantity - remainingToDeduct}, Requested: $quantity")
                }
            }
        }
    }

    override suspend fun deductStockFromBatch(
        batchId: String,
        quantity: Int,
        user: String,
        transactionType: InventoryTransactionType,
        notes: String?
    ) {
        database.withTransaction {
            val batch = vaccineDao.getBatchById(batchId)
                ?: throw IllegalStateException("Batch not found: $batchId")
            
            if (transactionType == InventoryTransactionType.VACCINATION && InventoryUtils.isExpired(batch.expiryDate)) {
                throw IllegalStateException("This vaccine batch expired on ${batch.expiryDate}. Please select another batch.")
            }

            if (batch.remainingQuantity < quantity) {
                throw IllegalStateException("Insufficient stock in batch ${batch.batchNumber}. Available: ${batch.remainingQuantity}, Requested: $quantity")
            }

            val currentTotal = vaccineDao.getTotalStockForVaccine(batch.vaccineId) ?: 0
            
            val updatedBatch = batch.copy(
                remainingQuantity = batch.remainingQuantity - quantity
            )
            vaccineDao.updateBatch(updatedBatch)

            val transaction = InventoryTransactionEntity(
                vaccineId = batch.vaccineId,
                batchId = batchId,
                transactionType = transactionType.name,
                quantity = -quantity,
                previousQuantity = currentTotal,
                currentQuantity = currentTotal - quantity,
                user = user,
                notes = notes
            )
            vaccineDao.insertTransaction(transaction)
            
            syncRepository.enqueue("BATCH", batchId, SyncOperation.UPDATE, SyncPriority.HIGH)
        }
    }

    override suspend fun addStockToBatch(
        batchId: String,
        quantity: Int,
        user: String,
        transactionType: InventoryTransactionType,
        notes: String?
    ) {
        database.withTransaction {
            val batch = vaccineDao.getBatchById(batchId)
                ?: throw IllegalStateException("Batch not found: $batchId")
            
            val currentTotal = vaccineDao.getTotalStockForVaccine(batch.vaccineId) ?: 0
            
            val updatedBatch = batch.copy(
                remainingQuantity = batch.remainingQuantity + quantity
            )
            vaccineDao.updateBatch(updatedBatch)

            val transaction = InventoryTransactionEntity(
                vaccineId = batch.vaccineId,
                batchId = batchId,
                transactionType = transactionType.name,
                quantity = quantity,
                previousQuantity = currentTotal,
                currentQuantity = currentTotal + quantity,
                user = user,
                notes = notes
            )
            vaccineDao.insertTransaction(transaction)
            
            syncRepository.enqueue("BATCH", batchId, SyncOperation.UPDATE, SyncPriority.HIGH)
        }
    }

    override suspend fun adjustStock(batchId: String, newQuantity: Int, user: String, reason: String) {
        database.withTransaction {
            val batch = vaccineDao.getBatchById(batchId) ?: return@withTransaction
            val currentTotal = vaccineDao.getTotalStockForVaccine(batch.vaccineId) ?: 0
            val diff = newQuantity - batch.remainingQuantity
            
            val updatedBatch = batch.copy(remainingQuantity = newQuantity)
            vaccineDao.updateBatch(updatedBatch)
            
            val transaction = InventoryTransactionEntity(
                vaccineId = batch.vaccineId,
                batchId = batchId,
                transactionType = InventoryTransactionType.MANUAL_ADJUSTMENT.name,
                quantity = diff,
                previousQuantity = currentTotal,
                currentQuantity = currentTotal + diff,
                user = user,
                notes = "Adjustment: $reason"
            )
            vaccineDao.insertTransaction(transaction)
            
            syncRepository.enqueue("BATCH", batchId, SyncOperation.UPDATE, SyncPriority.MEDIUM)

            auditLogger.logAction("Stock modified", null, "Batch: ${batch.batchNumber}, New Qty: $newQuantity, Reason: $reason")
        }
    }

    override suspend fun transferPatientTransactions(duplicateId: String, masterId: String) {
        vaccineDao.updatePatientIdInTransactions(duplicateId, masterId)
    }

    override suspend fun refreshInventory() {
        withContext(Dispatchers.IO) {
            try {
                // Refresh Vaccine Definitions
                val vaccineSnapshot = firestore.collection("vaccines").get().await()
                val vaccines = vaccineSnapshot.documents.mapNotNull { FirestoreMappers.toVaccineEntity(it) }
                vaccineDao.insertVaccines(vaccines)

                // Refresh Batches
                val batchSnapshot = firestore.collection("vaccine_batches").get().await()
                val batches = batchSnapshot.documents.mapNotNull { FirestoreMappers.toVaccineBatchEntity(it) }
                vaccineDao.insertBatches(batches)
            } catch (e: Exception) {
                // Error handling
            }
        }
    }
}
