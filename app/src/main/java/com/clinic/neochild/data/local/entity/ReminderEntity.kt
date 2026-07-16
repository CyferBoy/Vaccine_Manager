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
    val originalVisitId: String,
    val vaccineName: String,
    val dueDate: String, // Can be manually overridden
    val status: String, // ReminderStatus enum name
    val vaccinationSource: String? = null, // For EXTERNAL status
    val notes: String? = null,
    val lastReminderTime: Long = 0,
    val notificationSent: Boolean = false,
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)
