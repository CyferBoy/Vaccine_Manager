package com.clinic.neochild.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminder_audits")
data class ReminderAuditEntity(
    @PrimaryKey(autoGenerate = true) val auditId: Long = 0,
    val patientId: String,
    val originalVisitId: String,
    val vaccineName: String,
    val action: String, // COMPLETED, RESCHEDULED, DISMISSED, etc.
    val oldStatus: String?,
    val newStatus: String,
    val oldDate: String?,
    val newDate: String?,
    val priority: String?,
    val reminderEnabled: Boolean?,
    val performedBy: String,
    val timestamp: Long = System.currentTimeMillis(),
    val reason: String? = null,
    val notes: String? = null,
    val isSynced: Boolean = false
)
