package com.clinic.neochild.domain.repository

import com.clinic.neochild.data.local.entity.SyncQueueEntity
import com.clinic.neochild.data.model.SyncOperation
import com.clinic.neochild.data.model.SyncPriority
import kotlinx.coroutines.flow.Flow

interface SyncRepository {
    suspend fun enqueue(
        entityName: String,
        entityId: String,
        operation: SyncOperation,
        priority: SyncPriority = SyncPriority.MEDIUM
    )

    fun getPendingCount(): Flow<Int>
    fun getSyncQueue(): Flow<List<com.clinic.neochild.data.local.entity.SyncQueueEntity>>
    suspend fun processNextItems()
    suspend fun retryFailedItems()
    suspend fun clearSyncedItems()
}
