package com.clinic.neochild.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: String?,
    val vaccineId: String?,
    val type: String, // DUE_TODAY, TOMORROW, OVERDUE, LOW_STOCK, OUT_OF_STOCK, EXPIRY
    val dueDate: String,
    val status: String,
    val notificationSent: Boolean = false,
    val smsSent: Boolean = false,
    val lastReminderTime: Long = 0,
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
