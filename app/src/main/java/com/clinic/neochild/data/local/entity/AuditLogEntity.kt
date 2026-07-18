package com.clinic.neochild.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audit_logs",
    indices = [Index("patientId"), Index("timestamp")]
)
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: String?,
    val action: String,
    val details: String,
    val staffMember: String,
    val timestamp: Long = System.currentTimeMillis(),
    val device: String? = null,
    val isSynced: Boolean = false
)
