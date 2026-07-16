package com.clinic.neochild.domain.repository

import com.clinic.neochild.data.local.entity.ReminderEntity
import com.clinic.neochild.data.model.ReminderType
import com.clinic.neochild.data.model.Vaccination
import com.clinic.neochild.data.model.VaccinationSource
import com.clinic.neochild.utils.PendingRequirement
import kotlinx.coroutines.flow.Flow

/**
 * Single Source of Truth for Vaccination Reminders and Due Logic.
 */
interface ReminderRepository {
    // Core Data
    fun getUnsatisfiedRequirements(): Flow<List<PendingRequirement>>
    fun getAllReminderHistory(): Flow<List<ReminderEntity>>
    
    /**
     * The unified source for the Due list. 
     * Merges calculated requirements with manual overrides (status).
     */
    fun getDueList(): Flow<List<Vaccination>>
    
    // Notification-specific helpers
    fun getDueToday(): Flow<List<Vaccination>>
    fun getDueTomorrow(): Flow<List<Vaccination>>
    fun getOverdue(): Flow<List<Vaccination>>

    // Business Actions
    suspend fun markAsDone(requirement: PendingRequirement)
    suspend fun reschedule(requirement: PendingRequirement, newDate: String, reason: String)
    suspend fun markVaccinatedElsewhere(
        requirement: PendingRequirement,
        source: VaccinationSource,
        date: String,
        notes: String
    )
    suspend fun dismissReminder(requirement: PendingRequirement)

    // Notification/Worker Management
    suspend fun markCompleted(id: Long, timestamp: Long = System.currentTimeMillis())
    suspend fun markPatientRemindersCompleted(patientId: String, timestamp: Long = System.currentTimeMillis())
    suspend fun getPendingPatientReminder(patientId: String, type: ReminderType): ReminderEntity?
    suspend fun getPendingVaccineReminder(vaccineId: String, type: ReminderType): ReminderEntity?
    suspend fun insertReminder(reminder: ReminderEntity): Long
    suspend fun deleteOldReminders(days: Int)
    fun triggerImmediateCheck()
}
