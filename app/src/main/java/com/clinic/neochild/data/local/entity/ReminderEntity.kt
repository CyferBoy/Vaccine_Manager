package com.clinic.neochild.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reminders",
    indices = [Index(value = ["patientId", "originalVisitId", "vaccineName"], unique = true)]
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: String,
    val originalVisitId: String, // Can be visitId or a generic source id
    val vaccineName: String, // Or Follow-up Title
    val dueDate: String,
    val status: String, // ReminderStatus enum
    val priority: String = "NORMAL", // NORMAL, HIGH, URGENT
    val reminderEnabled: Boolean = true,
    val category: String = "VACCINATION", // VACCINATION, APPOINTMENT, GROWTH, etc.
    val vaccinationSource: String? = null,
    val notes: String? = null,
    val lastReminderTime: Long = 0,
    val notificationSent: Boolean = false,
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)
