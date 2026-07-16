package com.clinic.neochild.data.local.dao

import androidx.room.*
import com.clinic.neochild.data.local.entity.InventoryTransactionEntity
import com.clinic.neochild.data.local.entity.VaccineBatchEntity
import com.clinic.neochild.data.local.entity.VaccineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VaccineDao {
    // Vaccine Definition
    @Query("SELECT * FROM vaccines WHERE isDeleted = 0")
    fun getAllVaccines(): Flow<List<VaccineEntity>>

    @Query("SELECT * FROM vaccines WHERE id = :id AND isDeleted = 0")
    suspend fun getVaccineById(id: String): VaccineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaccine(vaccine: VaccineEntity)

    // Batches
    @Query("SELECT * FROM vaccine_batches WHERE vaccineId = :vaccineId AND isDeleted = 0 AND remainingQuantity > 0 ORDER BY expiryDate ASC")
    suspend fun getActiveBatchesByExpiry(vaccineId: String): List<VaccineBatchEntity>

    @Query("SELECT * FROM vaccine_batches WHERE isDeleted = 0")
    fun getAllBatches(): Flow<List<VaccineBatchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(batch: VaccineBatchEntity)

    @Update
    suspend fun updateBatch(batch: VaccineBatchEntity)

    // Transactions
    @Insert
    suspend fun insertTransaction(transaction: InventoryTransactionEntity)

    @Query("SELECT * FROM inventory_transactions WHERE vaccineId = :vaccineId ORDER BY timestamp DESC")
    fun getTransactionsForVaccine(vaccineId: String): Flow<List<InventoryTransactionEntity>>

    // Stock Summary
    @Query("SELECT SUM(remainingQuantity) FROM vaccine_batches WHERE vaccineId = :vaccineId AND isDeleted = 0")
    suspend fun getTotalStockForVaccine(vaccineId: String): Int?
}
