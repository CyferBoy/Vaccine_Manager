package com.clinic.neochild.domain.repository

import com.clinic.neochild.data.local.entity.InventoryTransactionEntity
import com.clinic.neochild.data.local.entity.VaccineBatchEntity
import com.clinic.neochild.data.local.entity.VaccineEntity
import com.clinic.neochild.data.model.InventoryTransactionType
import com.clinic.neochild.domain.model.InventoryItem
import kotlinx.coroutines.flow.Flow

interface InventoryRepository {
    fun getInventoryItems(): Flow<List<InventoryItem>>
    fun getAllVaccines(): Flow<List<VaccineEntity>>
    fun getVaccineBatches(vaccineId: String): Flow<List<VaccineBatchEntity>>
    fun getInventoryTransactions(vaccineId: String): Flow<List<InventoryTransactionEntity>>
    
    suspend fun addVaccineDefinition(vaccine: VaccineEntity)
    suspend fun addBatch(batch: VaccineBatchEntity, user: String)
    
    /**
     * Deduct stock using FEFO (First Expiry First Out)
     */
    suspend fun deductStock(
        vaccineId: String, 
        quantity: Int, 
        user: String, 
        transactionType: InventoryTransactionType,
        vaccinationId: String? = null,
        patientId: String? = null
    )

    suspend fun adjustStock(
        batchId: String, 
        newQuantity: Int, 
        user: String, 
        reason: String
    )
}
