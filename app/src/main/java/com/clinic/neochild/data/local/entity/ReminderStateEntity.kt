package com.clinic.neochild.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.clinic.neochild.domain.model.ReminderStatus

/**
 * Unified Reminder State for a specific vaccine requirement.
 * Replaces separate Due/Completed/Dismissed/External tables.
 * Enforces one state per requirement via unique index.
 */
@Entity(
    tableName = "reminder_states",
    foreignKeys = [
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["id"],
            childColumns = ["patientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["patientId", "originalVisitId", "vaccineName"], unique = true),
        Index("status"),
        Index("dueDate")
    ]
)
data class ReminderStateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: String,
    val originalVisitId: String,
    val vaccineName: String,
    val dueDate: String,
    val status: String, // ReminderStatus: ACTIVE, COMPLETED, DISMISSED, EXTERNAL
    
    // Metadata for various states
    val reminderDate: String? = null,
    val priority: String = "NORMAL",
    val reminderEnabled: Boolean = true,
    val category: String = "VACCINATION",
    val notes: String? = null,
    
    // Completion details
    val completionDate: Long? = null,
    val performedBy: String? = null,
    
    // Dismissal details
    val dismissalDate: Long? = null,
    val dismissalReason: String? = null,
    
    // External details
    val externalDate: String? = null,
    val source: String? = null,
    
    val lastReminderTime: Long = 0,
    val notificationSent: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)
