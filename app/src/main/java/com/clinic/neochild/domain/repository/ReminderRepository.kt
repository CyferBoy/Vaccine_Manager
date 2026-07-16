package com.clinic.neochild.domain.repository

import com.clinic.neochild.data.local.entity.ReminderAuditEntity
import com.clinic.neochild.data.local.entity.ReminderEntity
import com.clinic.neochild.data.model.ReminderType
import com.clinic.neochild.data.model.Vaccination
import com.clinic.neochild.data.model.VaccinationSource
import com.clinic.neochild.utils.PendingRequirement
import kotlinx.coroutines.flow.Flow

/**
 * Single Source of Truth for Vaccination Reminders.
 */
interface ReminderRepository {
    
    // Unified Data Sources
    fun getDueList(): Flow<List<Vaccination>>
    fun getDueToday(): Flow<List<Vaccination>>
    fun getDueTomorrow(): Flow<List<Vaccination>>
    fun getOverdue(): Flow<List<Vaccination>>
    
    // Core Business Actions (Atomic)
    suspend fun markAsDone(
        requirement: PendingRequirement,
        performedBy: String
    )
    
    suspend fun reschedule(
        requirement: PendingRequirement,
        newDate: String,
        reason: String,
        performedBy: String
    )
    
    suspend fun markVaccinatedElsewhere(
        requirement: PendingRequirement,
        source: VaccinationSource,
        date: String,
        notes: String,
        performedBy: String
    )
    
    suspend fun dismissReminder(
        requirement: PendingRequirement,
        reason: String,
        performedBy: String
    )
    
    suspend fun undoAction(auditId: Long, performedBy: String)

    // Audit Trail
    fun getAuditTrail(patientId: String): Flow<List<ReminderAuditEntity>>

    // Infrastructure / Internal
    fun triggerImmediateCheck()
    suspend fun syncWithRemote()
    
    // Legacy support (to be phased out or updated internally)
    suspend fun markCompleted(id: Long, timestamp: Long = System.currentTimeMillis())
    suspend fun insertReminder(reminder: ReminderEntity): Long
}
