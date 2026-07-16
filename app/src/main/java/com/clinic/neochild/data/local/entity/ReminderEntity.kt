package com.clinic.neochild.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: String,
    val originalVisitId: String,
    val vaccineName: String,
    val dueDate: String,
    val status: String, // ReminderStatus
    val vaccinationSource: String? = null, // For EXTERNAL status
    val notificationSent: Boolean = false,
    val smsSent: Boolean = false,
    val lastReminderTime: Long = 0,
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val type: String = "VACCINATION" // DUE_TODAY, etc are now logic-derived
)
