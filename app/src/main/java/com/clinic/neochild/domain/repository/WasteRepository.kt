package com.clinic.neochild.domain.repository

import com.clinic.neochild.domain.model.WasteRecord
import kotlinx.coroutines.flow.Flow

interface WasteRepository {
    fun getAllWaste(): Flow<List<WasteRecord>>
    suspend fun recordWaste(record: WasteRecord, user: String)
    suspend fun deleteWaste(id: String)
    fun getWasteCount(): Flow<Int>
}
