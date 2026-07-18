package com.clinic.neochild.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "due_reminders",
    indices = [Index(value = ["patientId", "originalVisitId", "vaccineName"], unique = true)]
)
data class DueReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: String,
    val originalVisitId: String,
    val vaccineName: String,
    val dueDate: String,
    val reminderDate: String,
    val status: String, // ACTIVE, RESCHEDULED, MISSED, SENT
    val priority: String = "NORMAL",
    val reminderEnabled: Boolean = true,
    val category: String = "VACCINATION",
    val notes: String? = null,
    val lastReminderTime: Long = 0,
    val notificationSent: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "completed_reminders",
    indices = [Index(value = ["patientId", "originalVisitId", "vaccineName"], unique = true)]
)
data class CompletedReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: String,
    val originalVisitId: String,
    val vaccineName: String,
    val dueDate: String,
    val completionDate: Long = System.currentTimeMillis(),
    val completedBy: String,
    val notes: String? = null,
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "dismissed_reminders",
    indices = [Index(value = ["patientId", "originalVisitId", "vaccineName"], unique = true)]
)
data class DismissedReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: String,
    val originalVisitId: String,
    val vaccineName: String,
    val dueDate: String,
    val dismissalDate: Long = System.currentTimeMillis(),
    val dismissedBy: String,
    val reason: String? = null,
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "external_reminders",
    indices = [Index(value = ["patientId", "originalVisitId", "vaccineName"], unique = true)]
)
data class ExternalReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: String,
    val originalVisitId: String,
    val vaccineName: String,
    val dueDate: String,
    val externalDate: String,
    val source: String,
    val recordedBy: String,
    val notes: String? = null,
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false
)

// Keep the old entity for migration purposes if needed, 
// but we'll use the new ones from now on.
@Entity(
    tableName = "reminders",
    indices = [Index(value = ["patientId", "originalVisitId", "vaccineName"], unique = true)]
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: String,
    val originalVisitId: String,
    val vaccineName: String,
    val dueDate: String,
    val status: String,
    val priority: String = "NORMAL",
    val reminderEnabled: Boolean = true,
    val category: String = "VACCINATION",
    val vaccinationSource: String? = null,
    val notes: String? = null,
    val lastReminderTime: Long = 0,
    val notificationSent: Boolean = false,
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)

