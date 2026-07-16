package com.clinic.neochild.domain.repository

import com.clinic.neochild.domain.model.Vaccine
import kotlinx.coroutines.flow.Flow

interface VaccineRepository {
    fun getInventory(): Flow<List<Vaccine>>
    suspend fun refreshInventory()
    suspend fun updateStock(vaccineId: String, newStock: Int)
    suspend fun deleteVaccine(id: String)
}
