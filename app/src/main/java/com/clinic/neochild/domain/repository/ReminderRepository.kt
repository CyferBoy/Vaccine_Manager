package com.clinic.neochild.domain.repository

import com.clinic.neochild.data.local.entity.ReminderEntity
import com.clinic.neochild.data.model.ReminderType
import kotlinx.coroutines.flow.Flow

interface ReminderRepository {
    fun getAllReminders(): Flow<List<ReminderEntity>>
    suspend fun getPendingPatientReminder(patientId: String, type: ReminderType): ReminderEntity?
    suspend fun getPendingVaccineReminder(vaccineId: String, type: ReminderType): ReminderEntity?
    suspend fun insertReminder(reminder: ReminderEntity): Long
    suspend fun updateReminder(reminder: ReminderEntity)
    suspend fun markCompleted(id: Long)
    suspend fun markPatientRemindersCompleted(patientId: String)
    suspend fun deleteOldReminders(days: Int)
}
