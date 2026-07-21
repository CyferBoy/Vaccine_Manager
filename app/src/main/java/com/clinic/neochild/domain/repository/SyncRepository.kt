package com.clinic.neochild.domain.repository

import com.clinic.neochild.core.model.SyncItem
import com.clinic.neochild.core.model.SyncOperation
import com.clinic.neochild.core.model.SyncPriority
import kotlinx.coroutines.flow.Flow

interface SyncRepository {
    suspend fun enqueue(
        entityName: String,
        entityId: String,
        operation: SyncOperation,
        priority: SyncPriority = SyncPriority.MEDIUM
    )

    fun getPendingCount(): Flow<Int>
    fun getSyncQueue(): Flow<List<SyncItem>>
    suspend fun processNextItems()
    suspend fun retryFailedItems()
    suspend fun clearSyncedItems()
    suspend fun deleteQueueItem(queueId: Long)
    suspend fun retryItem(queueId: Long)
    suspend fun deleteAllFailed()
}
