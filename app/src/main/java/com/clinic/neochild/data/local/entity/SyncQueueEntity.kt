package com.clinic.neochild.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.clinic.neochild.data.model.SyncOperation
import com.clinic.neochild.data.model.SyncPriority
import com.clinic.neochild.data.model.SyncStatus

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val queueId: Long = 0,
    val entityName: String, // e.g., "PATIENT", "VACCINATION", "INVENTORY"
    val entityId: String,
    val operation: String, // SyncOperation
    val priority: String = SyncPriority.MEDIUM.name,
    val status: String = SyncStatus.PENDING.name,
    val retryCount: Int = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
