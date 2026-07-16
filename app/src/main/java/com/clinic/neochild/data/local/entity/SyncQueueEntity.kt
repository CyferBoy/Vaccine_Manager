package com.clinic.neochild.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.clinic.neochild.domain.model.SyncItem
import com.clinic.neochild.domain.model.SyncOperation
import com.clinic.neochild.domain.model.SyncPriority
import com.clinic.neochild.domain.model.SyncStatus

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

fun SyncQueueEntity.toDomain() = SyncItem(
    id = queueId,
    entityName = entityName,
    entityId = entityId,
    operation = SyncOperation.valueOf(operation),
    priority = SyncPriority.valueOf(priority),
    status = SyncStatus.valueOf(status),
    retryCount = retryCount,
    lastError = lastError,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun SyncItem.toEntity() = SyncQueueEntity(
    queueId = id,
    entityName = entityName,
    entityId = entityId,
    operation = operation.name,
    priority = priority.name,
    status = status.name,
    retryCount = retryCount,
    lastError = lastError,
    createdAt = createdAt,
    updatedAt = updatedAt
)
