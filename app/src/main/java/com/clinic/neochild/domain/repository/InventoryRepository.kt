package com.clinic.neochild.domain.repository

import com.clinic.neochild.data.local.entity.InventoryTransactionEntity
import com.clinic.neochild.data.local.entity.VaccineBatchEntity
import com.clinic.neochild.data.local.entity.VaccineEntity
import com.clinic.neochild.domain.model.InventoryFilter
import com.clinic.neochild.domain.model.InventoryItem
import com.clinic.neochild.domain.model.InventorySort
import com.clinic.neochild.domain.model.InventoryTransactionType
import kotlinx.coroutines.flow.Flow

interface InventoryRepository {
    fun getInventoryItems(
        query: String = "",
        filter: InventoryFilter = InventoryFilter.ALL,
        sort: InventorySort = InventorySort.ALPHABETICAL
    ): Flow<List<InventoryItem>>
    
    fun getVaccineBatches(vaccineId: String): Flow<List<VaccineBatchEntity>>
    fun getInventoryTransactions(vaccineId: String): Flow<List<InventoryTransactionEntity>>
    suspend fun getBatchById(batchId: String): VaccineBatchEntity?
    
    suspend fun addStock(
        vaccine: VaccineEntity,
        batch: VaccineBatchEntity,
        user: String
    )
    
    suspend fun updateBatch(
        batch: VaccineBatchEntity,
        user: String,
        notes: String? = null
    )
    
    suspend fun deleteBatch(batchId: String, user: String)
    
    suspend fun deductStock(
        vaccineId: String, 
        quantity: Int, 
        user: String, 
        transactionType: InventoryTransactionType,
        vaccinationId: String? = null,
        patientId: String? = null
    )

    suspend fun deductStockFromBatch(
        batchId: String,
        quantity: Int,
        user: String,
        transactionType: InventoryTransactionType,
        notes: String? = null
    )

    suspend fun addStockToBatch(
        batchId: String,
        quantity: Int,
        user: String,
        transactionType: InventoryTransactionType,
        notes: String? = null
    )

    suspend fun adjustStock(
        batchId: String, 
        newQuantity: Int, 
        user: String, 
        reason: String
    )

    suspend fun transferPatientTransactions(duplicateId: String, masterId: String)
    suspend fun refreshInventory()
}
