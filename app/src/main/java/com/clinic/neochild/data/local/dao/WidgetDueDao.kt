package com.clinic.neochild.data.local.dao

import androidx.room.*
import com.clinic.neochild.data.local.entity.WidgetDueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WidgetDueDao {
    @Query("SELECT * FROM widget_due_cache ORDER BY id ASC")
    fun getDueItems(): Flow<List<WidgetDueEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<WidgetDueEntity>)

    @Query("DELETE FROM widget_due_cache")
    suspend fun clearCache()

    @Transaction
    suspend fun refreshCache(items: List<WidgetDueEntity>) {
        clearCache()
        insertItems(items)
    }
}
