package com.clinic.neochild.data.local.dao

import androidx.room.*
import com.clinic.neochild.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(item: SyncQueueEntity)

    @Query("SELECT * FROM sync_queue WHERE status = :status ORDER BY priority DESC, createdAt ASC")
    suspend fun getItemsByStatus(status: String): List<SyncQueueEntity>

    @Query("UPDATE sync_queue SET status = :status, updatedAt = :timestamp WHERE queueId = :id")
    suspend fun updateStatus(id: Long, status: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE sync_queue SET status = :status, retryCount = retryCount + 1, lastError = :error, updatedAt = :timestamp WHERE queueId = :id")
    suspend fun markFailed(id: Long, status: String, error: String, timestamp: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteItem(item: SyncQueueEntity)

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'PENDING'")
    fun getPendingCount(): Flow<Int>
}
