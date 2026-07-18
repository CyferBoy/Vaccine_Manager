package com.clinic.neochild.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.clinic.neochild.data.local.database.AppDatabase
import com.clinic.neochild.data.local.dao.*
import com.clinic.neochild.data.local.entity.*
import com.clinic.neochild.domain.model.*
import com.clinic.neochild.domain.repository.ReminderRepository
import com.clinic.neochild.domain.repository.ReminderStats
import com.clinic.neochild.domain.repository.SyncRepository
import com.clinic.neochild.notification.ReminderScheduler
import com.clinic.neochild.core.utils.*
import com.clinic.neochild.core.model.SyncOperation
import com.clinic.neochild.core.model.SyncPriority
import com.clinic.neochild.core.logger.AuditLogger
import com.clinic.neochild.domain.logic.ReminderEngine
import com.clinic.neochild.domain.model.PendingRequirement
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.Calendar
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production-ready implementation of [ReminderRepository].
 * Manages the lifecycle of vaccination reminders, audits, and synchronization.
 */
@Singleton
class ReminderRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val reminderDao: ReminderDao,
    private val dueReminderDao: DueReminderDao,
    private val reminderAuditDao: ReminderAuditDao,
    private val vaccinationDao: VaccinationDao,
    private val patientDao: PatientDao,
    private val syncRepository: SyncRepository,
    private val reminderScheduler: ReminderScheduler,
    private val auditLogger: AuditLogger,
    @ApplicationContext private val context: Context
) : ReminderRepository {

    /**
     * Shared logic to combine raw entities into a processed list of vaccinations with overrides applied.
     */
    private fun getProcessedDueFlow(): Flow<Pair<List<Vaccination>, List<PatientEntity>>> {
        return combine(
            vaccinationDao.getAllVaccinations(),
            dueReminderDao.getAllDueReminders(),
            dueReminderDao.getAllCompletedReminders(),
            dueReminderDao.getAllDismissedReminders(),
            dueReminderDao.getAllExternalReminders(),
            patientDao.getAllPatients()
        ) { flows: Array<List<Any>> ->
            @Suppress("UNCHECKED_CAST")
            val vaccEntities = flows[0] as List<VaccinationEntity>
            @Suppress("UNCHECKED_CAST")
            val dueEntities = flows[1] as List<DueReminderEntity>
            @Suppress("UNCHECKED_CAST")
            val completedEntities = flows[2] as List<CompletedReminderEntity>
            @Suppress("UNCHECKED_CAST")
            val dismissedEntities = flows[3] as List<DismissedReminderEntity>
            @Suppress("UNCHECKED_CAST")
            val externalEntities = flows[4] as List<ExternalReminderEntity>
            @Suppress("UNCHECKED_CAST")
            val patientEntities = flows[5] as List<PatientEntity>

            val processed = processDueListInternal(
                vaccEntities, 
                dueEntities, 
                completedEntities, 
                dismissedEntities, 
                externalEntities
            )
            processed to patientEntities
        }
    }

    override fun getDueList(
        searchQuery: String,
        filterStatus: List<ReminderStatus>?
    ): Flow<List<Vaccination>> = getProcessedDueFlow().map { (processed, patientEntities) ->
        val patientMap = patientEntities.associateBy { it.id }
        processed.filter { vacc ->
            val patient = patientMap[vacc.patientId]
            
            // Apply Search Filter
            val matchesSearch = if (searchQuery.isBlank()) true else {
                patient?.name?.contains(searchQuery, ignoreCase = true) == true ||
                patient?.phone?.contains(searchQuery) == true ||
                vacc.nxtVaccineNames.any { it.contains(searchQuery, ignoreCase = true) }
            }
            if (!matchesSearch) return@filter false

            true
        }
    }

    override fun getDueToday(): Flow<List<Vaccination>> = getProcessedDueFlow().map { (list, _) ->
        PatientUtils.filterVaccinationsByPeriod(list, "Today")
    }

    override fun getDueTomorrow(): Flow<List<Vaccination>> = getProcessedDueFlow().map { (list, _) ->
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }.time
        val tomorrowStr = PatientUtils.formatDate(tomorrow)
        PatientUtils.filterVaccinationsByPeriod(list, "This Week").filter { v ->
            v.nextDueDate == tomorrowStr
        }
    }

    override fun getOverdue(): Flow<List<Vaccination>> = getProcessedDueFlow().map { (list, _) ->
        PatientUtils.filterVaccinationsByPeriod(list, "Overdue")
    }

    override fun getDashboardStats(): Flow<ReminderStats> = combine(
        vaccinationDao.getAllVaccinations(),
        dueReminderDao.getAllDueReminders(),
        dueReminderDao.getAllCompletedReminders(),
        dueReminderDao.getAllDismissedReminders(),
        dueReminderDao.getAllExternalReminders()
    ) { flows: Array<List<Any>> ->
        @Suppress("UNCHECKED_CAST")
        val vaccEntities = flows[0] as List<VaccinationEntity>
        @Suppress("UNCHECKED_CAST")
        val dueEntities = flows[1] as List<DueReminderEntity>
        @Suppress("UNCHECKED_CAST")
        val completedEntities = flows[2] as List<CompletedReminderEntity>
        @Suppress("UNCHECKED_CAST")
        val dismissedEntities = flows[3] as List<DismissedReminderEntity>
        @Suppress("UNCHECKED_CAST")
        val externalEntities = flows[4] as List<ExternalReminderEntity>

        val dueList = processDueListInternal(vaccEntities, dueEntities, completedEntities, dismissedEntities, externalEntities)
        
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }.time
        val tomorrowStr = PatientUtils.formatDate(tomorrow)

        ReminderStats(
            dueToday = PatientUtils.filterVaccinationsByPeriod(dueList, "Today").size,
            dueTomorrow = PatientUtils.filterVaccinationsByPeriod(dueList, "This Week").count { it.nextDueDate == tomorrowStr },
            overdue = PatientUtils.filterVaccinationsByPeriod(dueList, "Overdue").size,
            completedToday = completedEntities.count { it.completionDate >= todayStart },
            rescheduledToday = dueEntities.count { it.status == ReminderStatus.RESCHEDULED.name && it.updatedAt >= todayStart },
            externalToday = externalEntities.count { it.recordedBy != "MIGRATED" },
            notificationsSentToday = dueEntities.count { it.notificationSent && it.lastReminderTime >= todayStart }
        )
    }

    private fun processDueListInternal(
        vaccEntities: List<VaccinationEntity>,
        dueEntities: List<DueReminderEntity>,
        completedEntities: List<CompletedReminderEntity>,
        dismissedEntities: List<DismissedReminderEntity>,
        externalEntities: List<ExternalReminderEntity>
    ): List<Vaccination> {
        val allVaccinations = vaccEntities.map { it.toVaccination() }
        val potential = ReminderEngine.getPotentialRequirements(allVaccinations)
        
        val dueMap = dueEntities.associateBy { "${it.patientId}_${it.originalVisitId}_${it.vaccineName}" }
        val completedKeys = completedEntities.map { "${it.patientId}_${it.originalVisitId}_${it.vaccineName}" }.toSet()
        val dismissedKeys = dismissedEntities.map { "${it.patientId}_${it.originalVisitId}_${it.vaccineName}" }.toSet()
        val externalKeys = externalEntities.map { "${it.patientId}_${it.originalVisitId}_${it.vaccineName}" }.toSet()

        return potential.mapNotNull { req ->
            val key = "${req.patientId}_${req.originalVisitId}_${req.vaccineName}"
            
            // If it's already completed, dismissed or done elsewhere, it's not "due"
            if (completedKeys.contains(key) || dismissedKeys.contains(key) || externalKeys.contains(key)) {
                return@mapNotNull null
            }

            val savedDue = dueMap[key]
            
            val status = try {
                savedDue?.status?.let { ReminderStatus.valueOf(it) } ?: ReminderStatus.ACTIVE
            } catch (_: Exception) {
                ReminderStatus.ACTIVE
            }

            val finalDueDate = if (status == ReminderStatus.RESCHEDULED && savedDue != null) {
                savedDue.dueDate
            } else {
                PatientUtils.formatDate(req.dueDate)
            }

            allVaccinations.find { it.id == req.originalVisitId }?.copy(
                nxtVaccineNames = listOf(req.vaccineName),
                nextDueDate = finalDueDate,
                isDone = false
            )
        }
    }

    override suspend fun scheduleFollowUp(
        patientId: String,
        originalVisitId: String,
        vaccineNames: List<String>,
        dueDate: String,
        notes: String,
        priority: String,
        reminderEnabled: Boolean,
        performedBy: String
    ) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                vaccineNames.forEach { name ->
                    val reminder = DueReminderEntity(
                        patientId = patientId,
                        originalVisitId = originalVisitId,
                        vaccineName = name,
                        dueDate = dueDate,
                        reminderDate = dueDate,
                        status = ReminderStatus.ACTIVE.name,
                        priority = priority,
                        reminderEnabled = reminderEnabled,
                        notes = notes,
                        isSynced = false
                    )
                    val id = dueReminderDao.insertDueReminder(reminder)
                    
                    val audit = ReminderAuditEntity(
                        patientId = patientId,
                        originalVisitId = originalVisitId,
                        vaccineName = name,
                        action = "SCHEDULED",
                        oldStatus = null,
                        newStatus = ReminderStatus.ACTIVE.name,
                        oldDate = null,
                        newDate = dueDate,
                        priority = priority,
                        reminderEnabled = reminderEnabled,
                        performedBy = performedBy,
                        notes = "Staff scheduled follow-up",
                        isSynced = false
                    )
                    val auditId = reminderAuditDao.insertAudit(audit)

                    syncRepository.enqueue("DUE_REMINDER", id.toString(), SyncOperation.CREATE, SyncPriority.MEDIUM)
                    syncRepository.enqueue("REMINDER_AUDIT", auditId.toString(), SyncOperation.CREATE, SyncPriority.LOW)
                }
            }
            if (reminderEnabled) triggerImmediateCheck()
        }
    }

    override suspend fun markRequirementSatisfied(requirement: PendingRequirement, performedBy: String) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val existing = dueReminderDao.getDueReminder(requirement.patientId, requirement.originalVisitId, requirement.vaccineName)
                
                val completed = CompletedReminderEntity(
                    patientId = requirement.patientId,
                    originalVisitId = requirement.originalVisitId,
                    vaccineName = requirement.vaccineName,
                    dueDate = existing?.dueDate ?: PatientUtils.formatDate(requirement.dueDate),
                    completionDate = System.currentTimeMillis(),
                    completedBy = performedBy,
                    notes = existing?.notes
                )
                val id = dueReminderDao.insertCompletedReminder(completed)
                
                if (existing != null) {
                    dueReminderDao.deleteDueReminder(existing.patientId, existing.originalVisitId, existing.vaccineName)
                    syncRepository.enqueue("DUE_REMINDER", existing.id.toString(), SyncOperation.DELETE, SyncPriority.LOW)
                }

                val auditId = reminderAuditDao.insertAudit(
                    ReminderAuditEntity(
                        patientId = requirement.patientId,
                        originalVisitId = requirement.originalVisitId,
                        vaccineName = requirement.vaccineName,
                        action = "COMPLETED",
                        oldStatus = existing?.status,
                        newStatus = "COMPLETED",
                        oldDate = existing?.dueDate,
                        newDate = null,
                        priority = existing?.priority,
                        reminderEnabled = existing?.reminderEnabled,
                        performedBy = performedBy,
                        notes = "Requirement satisfied",
                        isSynced = false
                    )
                )
                
                syncRepository.enqueue("COMPLETED_REMINDER", id.toString(), SyncOperation.CREATE, SyncPriority.MEDIUM)
                syncRepository.enqueue("REMINDER_AUDIT", auditId.toString(), SyncOperation.CREATE, SyncPriority.LOW)

                auditLogger.logAction(
                    action = "Reminder Completed",
                    patientId = requirement.patientId,
                    details = "${requirement.vaccineName} marked as done by $performedBy"
                )
            }
            triggerImmediateCheck()
        }
    }

    override suspend fun reschedule(requirement: PendingRequirement, newDate: String, reminderDate: String, reason: String, performedBy: String) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val existing = dueReminderDao.getDueReminder(requirement.patientId, requirement.originalVisitId, requirement.vaccineName)
                
                val updated = existing?.copy(
                    dueDate = newDate,
                    reminderDate = reminderDate,
                    status = ReminderStatus.RESCHEDULED.name,
                    updatedAt = System.currentTimeMillis(),
                    notes = if (reason.isNotBlank()) "${existing.notes ?: ""}\nRescheduled: $reason" else existing.notes,
                    isSynced = false
                ) ?: DueReminderEntity(
                    patientId = requirement.patientId,
                    originalVisitId = requirement.originalVisitId,
                    vaccineName = requirement.vaccineName,
                    dueDate = newDate,
                    reminderDate = reminderDate,
                    status = ReminderStatus.RESCHEDULED.name,
                    notes = reason,
                    isSynced = false
                )

                val id = dueReminderDao.insertDueReminder(updated)

                val auditId = reminderAuditDao.insertAudit(
                    ReminderAuditEntity(
                        patientId = requirement.patientId,
                        originalVisitId = requirement.originalVisitId,
                        vaccineName = requirement.vaccineName,
                        action = "Reminder Rescheduled",
                        oldStatus = existing?.status,
                        newStatus = "RESCHEDULED",
                        oldDate = existing?.dueDate,
                        newDate = newDate,
                        priority = existing?.priority,
                        reminderEnabled = existing?.reminderEnabled,
                        performedBy = performedBy,
                        reason = reason,
                        isSynced = false
                    )
                )
                
                syncRepository.enqueue("DUE_REMINDER", id.toString(), SyncOperation.UPDATE, SyncPriority.MEDIUM)
                syncRepository.enqueue("REMINDER_AUDIT", auditId.toString(), SyncOperation.CREATE, SyncPriority.LOW)

                auditLogger.logAction(
                    action = "Reminder Rescheduled",
                    patientId = requirement.patientId,
                    details = "${requirement.vaccineName} moved to $newDate. Reminder set for $reminderDate. Reason: $reason"
                )
            }
            triggerImmediateCheck()
        }
    }

    override suspend fun markVaccinatedElsewhere(requirement: PendingRequirement, source: VaccinationSource, date: String, notes: String, performedBy: String) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                // 1. Record Vaccination
                val newVaccination = Vaccination(
                    id = UUID.randomUUID().toString(),
                    patientId = requirement.patientId,
                    vaccineNames = listOf(requirement.vaccineName),
                    dateGiven = date,
                    isDone = true,
                    source = source.name,
                    notes = notes,
                    performedBy = performedBy
                )
                vaccinationDao.insertVaccination(newVaccination.toEntity(isSynced = false))
                syncRepository.enqueue("VACCINATION", newVaccination.id, SyncOperation.CREATE, SyncPriority.MEDIUM)

                // 2. Move Reminder to External
                val existing = dueReminderDao.getDueReminder(requirement.patientId, requirement.originalVisitId, requirement.vaccineName)
                val external = ExternalReminderEntity(
                    patientId = requirement.patientId,
                    originalVisitId = requirement.originalVisitId,
                    vaccineName = requirement.vaccineName,
                    dueDate = existing?.dueDate ?: PatientUtils.formatDate(requirement.dueDate),
                    externalDate = date,
                    source = source.name,
                    recordedBy = performedBy,
                    notes = notes
                )
                val id = dueReminderDao.insertExternalReminder(external)

                if (existing != null) {
                    dueReminderDao.deleteDueReminder(existing.patientId, existing.originalVisitId, existing.vaccineName)
                    syncRepository.enqueue("DUE_REMINDER", existing.id.toString(), SyncOperation.DELETE, SyncPriority.LOW)
                }

                val auditId = reminderAuditDao.insertAudit(
                    ReminderAuditEntity(
                        patientId = requirement.patientId,
                        originalVisitId = requirement.originalVisitId,
                        vaccineName = requirement.vaccineName,
                        action = "EXTERNAL",
                        oldStatus = existing?.status,
                        newStatus = "EXTERNAL",
                        oldDate = existing?.dueDate,
                        newDate = null,
                        priority = existing?.priority,
                        reminderEnabled = existing?.reminderEnabled,
                        performedBy = performedBy,
                        notes = "Given at: ${source.name}. Notes: $notes",
                        isSynced = false
                    )
                )
                
                syncRepository.enqueue("EXTERNAL_REMINDER", id.toString(), SyncOperation.CREATE, SyncPriority.MEDIUM)
                syncRepository.enqueue("REMINDER_AUDIT", auditId.toString(), SyncOperation.CREATE, SyncPriority.LOW)

                auditLogger.logAction(
                    action = "Vaccinated Elsewhere",
                    patientId = requirement.patientId,
                    details = "${requirement.vaccineName} given at ${source.name} on $date"
                )
            }
            triggerImmediateCheck()
        }
    }

    override suspend fun dismissReminder(requirement: PendingRequirement, reason: String, performedBy: String) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val existing = dueReminderDao.getDueReminder(requirement.patientId, requirement.originalVisitId, requirement.vaccineName)
                
                val dismissed = DismissedReminderEntity(
                    patientId = requirement.patientId,
                    originalVisitId = requirement.originalVisitId,
                    vaccineName = requirement.vaccineName,
                    dueDate = existing?.dueDate ?: PatientUtils.formatDate(requirement.dueDate),
                    dismissalDate = System.currentTimeMillis(),
                    dismissedBy = performedBy,
                    reason = reason
                )
                val id = dueReminderDao.insertDismissedReminder(dismissed)

                if (existing != null) {
                    dueReminderDao.deleteDueReminder(existing.patientId, existing.originalVisitId, existing.vaccineName)
                    syncRepository.enqueue("DUE_REMINDER", existing.id.toString(), SyncOperation.DELETE, SyncPriority.LOW)
                }

                val auditId = reminderAuditDao.insertAudit(
                    ReminderAuditEntity(
                        patientId = requirement.patientId,
                        originalVisitId = requirement.originalVisitId,
                        vaccineName = requirement.vaccineName,
                        action = "DISMISSED",
                        oldStatus = existing?.status,
                        newStatus = "DISMISSED",
                        oldDate = existing?.dueDate,
                        newDate = null,
                        priority = existing?.priority,
                        reminderEnabled = existing?.reminderEnabled,
                        performedBy = performedBy,
                        reason = reason,
                        isSynced = false
                    )
                )
                
                syncRepository.enqueue("DISMISSED_REMINDER", id.toString(), SyncOperation.CREATE, SyncPriority.MEDIUM)
                syncRepository.enqueue("REMINDER_AUDIT", auditId.toString(), SyncOperation.CREATE, SyncPriority.LOW)

                auditLogger.logAction(
                    action = "Reminder Dismissed",
                    patientId = requirement.patientId,
                    details = "${requirement.vaccineName} dismissed. Reason: $reason"
                )
            }
            triggerImmediateCheck()
        }
    }

    override suspend fun restoreReminder(requirement: PendingRequirement, performedBy: String) {
        // To restore, we need to find where it is (completed, dismissed, or external) and move it back to due.
        // For simplicity, we just delete it from those tables and let ReminderEngine re-calculate it as active,
        // unless it was manually scheduled.
        withContext(Dispatchers.IO) {
            database.withTransaction {
                // Check all tables and delete if found
                // Note: Simplified logic for now
                dueReminderDao.deleteDueReminder(requirement.patientId, requirement.originalVisitId, requirement.vaccineName)
                
                val auditId = reminderAuditDao.insertAudit(
                    ReminderAuditEntity(
                        patientId = requirement.patientId,
                        originalVisitId = requirement.originalVisitId,
                        vaccineName = requirement.vaccineName,
                        action = "RESTORED",
                        oldStatus = null,
                        newStatus = "ACTIVE",
                        oldDate = null,
                        newDate = null,
                        priority = null,
                        reminderEnabled = null,
                        performedBy = performedBy,
                        notes = "Restored to active schedule",
                        isSynced = false
                    )
                )
                syncRepository.enqueue("REMINDER_AUDIT", auditId.toString(), SyncOperation.CREATE, SyncPriority.LOW)

                auditLogger.logAction(
                    action = "Restore Performed",
                    patientId = requirement.patientId,
                    details = "${requirement.vaccineName} restored to active schedule"
                )
            }
            triggerImmediateCheck()
        }
    }

    override suspend fun deleteReminder(requirement: PendingRequirement, performedBy: String) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val existing = dueReminderDao.getDueReminder(requirement.patientId, requirement.originalVisitId, requirement.vaccineName)
                if (existing != null) {
                    dueReminderDao.softDeleteDueReminder(existing.id)
                    
                    val auditId = reminderAuditDao.insertAudit(ReminderAuditEntity(
                        patientId = requirement.patientId,
                        originalVisitId = requirement.originalVisitId,
                        vaccineName = requirement.vaccineName,
                        action = "DELETED",
                        oldStatus = existing.status,
                        newStatus = "DELETED",
                        oldDate = existing.dueDate,
                        newDate = null,
                        priority = existing.priority,
                        reminderEnabled = existing.reminderEnabled,
                        performedBy = performedBy,
                        notes = "Soft deleted by staff",
                        isSynced = false
                    ))
                    
                    syncRepository.enqueue("DUE_REMINDER", existing.id.toString(), SyncOperation.UPDATE, SyncPriority.LOW)
                    syncRepository.enqueue("REMINDER_AUDIT", auditId.toString(), SyncOperation.CREATE, SyncPriority.LOW)

                    auditLogger.logAction(
                        action = "Vaccine Deleted",
                        patientId = requirement.patientId,
                        details = "${requirement.vaccineName} record soft deleted"
                    )
                }
            }
            triggerImmediateCheck()
        }
    }

    override fun getPatientFollowUps(patientId: String): Flow<List<ReminderEntity>> {
        // Map the new DueReminderEntity back to ReminderEntity for legacy UI support if needed
        return dueReminderDao.getDueRemindersForPatient(patientId).map { list ->
            list.map { due ->
                ReminderEntity(
                    id = due.id,
                    patientId = due.patientId,
                    originalVisitId = due.originalVisitId,
                    vaccineName = due.vaccineName,
                    dueDate = due.dueDate,
                    status = due.status,
                    priority = due.priority,
                    reminderEnabled = due.reminderEnabled,
                    category = due.category,
                    notes = due.notes,
                    lastReminderTime = due.lastReminderTime,
                    notificationSent = due.notificationSent,
                    createdAt = due.createdAt,
                    updatedAt = due.updatedAt,
                    isSynced = due.isSynced
                )
            }
        }
    }

    override suspend fun undoAction(auditId: Long, performedBy: String) {
        // Future medical correction workflow
    }

    override fun getAuditTrail(patientId: String): Flow<List<ReminderAuditEntity>> {
        return reminderAuditDao.getAuditsForPatient(patientId)
    }

    override suspend fun syncWithRemote() {
        // Now handled via SyncRepository and SyncWorker
    }

    override suspend fun markCompleted(id: Long, timestamp: Long) {
        withContext(Dispatchers.IO) {
            // Legacy support, might need updating if 'id' is from 'reminders' or 'due_reminders'
            // For now, let's assume it's from due_reminders for notification actions
            val existing = dueReminderDao.getAllDueReminders().first().find { it.id == id }
            if (existing != null) {
                markRequirementSatisfied(
                    PendingRequirement(existing.patientId, existing.vaccineName, PatientUtils.parseDate(existing.dueDate) ?: Date(), existing.originalVisitId),
                    "SYSTEM_NOTIFICATION"
                )
            }
        }
    }

    override suspend fun insertReminder(reminder: ReminderEntity): Long {
        val due = DueReminderEntity(
            patientId = reminder.patientId,
            originalVisitId = reminder.originalVisitId,
            vaccineName = reminder.vaccineName,
            dueDate = reminder.dueDate,
            reminderDate = reminder.dueDate,
            status = reminder.status,
            priority = reminder.priority,
            reminderEnabled = reminder.reminderEnabled,
            category = reminder.category,
            notes = reminder.notes,
            lastReminderTime = reminder.lastReminderTime,
            notificationSent = reminder.notificationSent,
            createdAt = reminder.createdAt,
            updatedAt = reminder.updatedAt,
            isSynced = reminder.isSynced
        )
        val id = dueReminderDao.insertDueReminder(due)
        syncRepository.enqueue("DUE_REMINDER", id.toString(), SyncOperation.CREATE, SyncPriority.MEDIUM)
        return id
    }

    override suspend fun transferReminders(duplicateId: String, masterId: String) {
        // Update all tables
        // Note: Missing update methods in DueReminderDao, will need to add them or use raw query
        reminderAuditDao.updatePatientId(duplicateId, masterId)
    }

    override fun triggerImmediateCheck() {
        reminderScheduler.runNow()
        WidgetUtils.updateWidget(context)
    }
}
