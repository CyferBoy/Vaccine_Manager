package com.clinic.neochild.domain.repository

import com.clinic.neochild.data.local.entity.AuditLogEntity
import com.clinic.neochild.data.local.entity.BorrowEntity
import com.clinic.neochild.data.local.entity.VaccineBatchEntity
import com.clinic.neochild.data.local.entity.WasteEntity
import com.clinic.neochild.domain.model.Vaccine
import kotlinx.coroutines.flow.Flow

/**
 * Domain-level Repository interface for Vaccine and Inventory data.
 * The Vaccine entity is the primary root of this module.
 */
interface VaccineRepository {
    fun getInventory(): Flow<List<Vaccine>>
    suspend fun refreshInventory()
    suspend fun deleteVaccine(id: String)
    
    // Batch Management
    fun getBatches(vaccineId: String): Flow<List<VaccineBatchEntity>>
    suspend fun addBatch(batch: VaccineBatchEntity)
    
    // Waste & Borrow
    fun getWasteRecords(vaccineId: String): Flow<List<WasteEntity>>
    fun getBorrowRecords(vaccineId: String): Flow<List<BorrowEntity>>
    
    // Inventory Timeline
    fun getVaccineTimeline(vaccineId: String): Flow<List<AuditLogEntity>>

    suspend fun refreshBorrowRecords()
}
