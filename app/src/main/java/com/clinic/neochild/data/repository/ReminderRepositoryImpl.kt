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
import com.clinic.neochild.domain.logic.ReminderEngine
import com.clinic.neochild.domain.model.PendingRequirement
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.Calendar
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
    private val reminderAuditDao: ReminderAuditDao,
    private val vaccinationDao: VaccinationDao,
    private val patientDao: PatientDao,
    private val syncRepository: SyncRepository,
    private val reminderScheduler: ReminderScheduler,
    @ApplicationContext private val context: Context
) : ReminderRepository {

    /**
     * Shared logic to combine raw entities into a processed list of vaccinations with overrides applied.
     */
    private fun getProcessedDueFlow(): Flow<Pair<List<Vaccination>, List<PatientEntity>>> {
        return combine(
            vaccinationDao.getAllVaccinations(),
            reminderDao.getAllReminders(),
            patientDao.getAllPatients()
        ) { vaccEntities, reminderEntities, patientEntities ->
            val processed = processDueListInternal(vaccEntities, reminderEntities)
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
        reminderDao.getAllReminders(),
        patientDao.getAllPatients()
    ) { vaccEntities, reminderEntities, _ ->
        val dueList = processDueListInternal(vaccEntities, reminderEntities)
        
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }.time
        val tomorrowStr = PatientUtils.formatDate(tomorrow)

        ReminderStats(
            dueToday = PatientUtils.filterVaccinationsByPeriod(dueList, "Today").size,
            dueTomorrow = PatientUtils.filterVaccinationsByPeriod(dueList, "This Week").count { it.nextDueDate == tomorrowStr },
            overdue = PatientUtils.filterVaccinationsByPeriod(dueList, "Overdue").size,
            completedToday = reminderEntities.count { it.status == ReminderStatus.COMPLETED.name && it.updatedAt >= todayStart },
            rescheduledToday = reminderEntities.count { it.status == ReminderStatus.RESCHEDULED.name && it.updatedAt >= todayStart },
            externalToday = reminderEntities.count { it.status == ReminderStatus.EXTERNAL.name && it.updatedAt >= todayStart },
            notificationsSentToday = reminderEntities.count { it.notificationSent && it.lastReminderTime >= todayStart }
        )
    }

    private fun processDueListInternal(
        vaccEntities: List<VaccinationEntity>,
        reminderEntities: List<ReminderEntity>
    ): List<Vaccination> {
        val allVaccinations = vaccEntities.map { it.toVaccination() }
        val potential = ReminderEngine.getPotentialRequirements(allVaccinations)
        val reminderMap = reminderEntities.associateBy { "${it.patientId}_${it.originalVisitId}_${it.vaccineName}" }

        return potential.mapNotNull { req ->
            val key = "${req.patientId}_${req.originalVisitId}_${req.vaccineName}"
            val savedReminder = reminderMap[key]

            val status = try {
                savedReminder?.status?.let { ReminderStatus.valueOf(it) } ?: ReminderStatus.ACTIVE
            } catch (_: Exception) {
                ReminderStatus.ACTIVE
            }

            // Core Business Logic: Filter out hidden/terminal states
            if (status == ReminderStatus.COMPLETED || status == ReminderStatus.DISMISSED || status == ReminderStatus.EXTERNAL) {
                return@mapNotNull null
            }

            val finalDueDate = if (status == ReminderStatus.RESCHEDULED && savedReminder != null) {
                savedReminder.dueDate
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
                    val reminder = ReminderEntity(
                        patientId = patientId,
                        originalVisitId = originalVisitId,
                        vaccineName = name,
                        dueDate = dueDate,
                        status = ReminderStatus.ACTIVE.name,
                        priority = priority,
                        reminderEnabled = reminderEnabled,
                        notes = notes,
                        isSynced = false
                    )
                    val id = reminderDao.insertOrUpdate(reminder)
                    
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

                    syncRepository.enqueue("REMINDER_OVERRIDE", id.toString(), SyncOperation.CREATE, SyncPriority.MEDIUM)
                    syncRepository.enqueue("REMINDER_AUDIT", auditId.toString(), SyncOperation.CREATE, SyncPriority.LOW)
                }
            }
            if (reminderEnabled) triggerImmediateCheck()
        }
    }

    override suspend fun markRequirementSatisfied(requirement: PendingRequirement, performedBy: String) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                updateReminderStateAndAudit(
                    req = requirement,
                    newStatus = ReminderStatus.COMPLETED,
                    performedBy = performedBy,
                    notes = "Requirement satisfied"
                )
            }
            triggerImmediateCheck()
        }
    }

    override suspend fun reschedule(requirement: PendingRequirement, newDate: String, reason: String, performedBy: String) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                updateReminderStateAndAudit(
                    req = requirement,
                    newStatus = ReminderStatus.RESCHEDULED,
                    performedBy = performedBy,
                    newDate = newDate,
                    reason = reason
                )
            }
            triggerImmediateCheck()
        }
    }

    override suspend fun markVaccinatedElsewhere(requirement: PendingRequirement, source: VaccinationSource, date: String, notes: String, performedBy: String) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
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

                updateReminderStateAndAudit(
                    req = requirement,
                    newStatus = ReminderStatus.EXTERNAL,
                    performedBy = performedBy,
                    notes = "Given at: ${source.name}. Notes: $notes"
                )
            }
            triggerImmediateCheck()
        }
    }

    override suspend fun dismissReminder(requirement: PendingRequirement, reason: String, performedBy: String) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                updateReminderStateAndAudit(
                    req = requirement,
                    newStatus = ReminderStatus.DISMISSED,
                    performedBy = performedBy,
                    reason = reason
                )
            }
            triggerImmediateCheck()
        }
    }

    override suspend fun restoreReminder(requirement: PendingRequirement, performedBy: String) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                updateReminderStateAndAudit(
                    req = requirement,
                    newStatus = ReminderStatus.ACTIVE,
                    performedBy = performedBy,
                    notes = "Restored by staff"
                )
            }
            triggerImmediateCheck()
        }
    }

    override suspend fun deleteReminder(requirement: PendingRequirement, performedBy: String) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val existing = reminderDao.getReminderState(requirement.patientId, requirement.originalVisitId, requirement.vaccineName)
                reminderDao.deleteReminder(requirement.patientId, requirement.originalVisitId, requirement.vaccineName)
                
                val auditId = reminderAuditDao.insertAudit(ReminderAuditEntity(
                    patientId = requirement.patientId,
                    originalVisitId = requirement.originalVisitId,
                    vaccineName = requirement.vaccineName,
                    action = "DELETED",
                    oldStatus = existing?.status,
                    newStatus = "DELETED",
                    oldDate = existing?.dueDate,
                    newDate = null,
                    priority = existing?.priority,
                    reminderEnabled = existing?.reminderEnabled,
                    performedBy = performedBy,
                    notes = "Deleted by admin",
                    isSynced = false
                ))
                
                if (existing != null) {
                    syncRepository.enqueue("REMINDER_OVERRIDE", existing.id.toString(), SyncOperation.DELETE, SyncPriority.LOW)
                }
                syncRepository.enqueue("REMINDER_AUDIT", auditId.toString(), SyncOperation.CREATE, SyncPriority.LOW)
            }
            triggerImmediateCheck()
        }
    }

    override fun getPatientFollowUps(patientId: String): Flow<List<ReminderEntity>> {
        return reminderDao.getFollowUpsForPatient(patientId)
    }

    override suspend fun undoAction(auditId: Long, performedBy: String) {
        // Future medical correction workflow
    }

    override fun getAuditTrail(patientId: String): Flow<List<ReminderAuditEntity>> {
        return reminderAuditDao.getAuditsForPatient(patientId)
    }

    private suspend fun updateReminderStateAndAudit(
        req: PendingRequirement,
        newStatus: ReminderStatus,
        performedBy: String,
        newDate: String? = null,
        reason: String? = null,
        notes: String? = null,
        priority: String? = null,
        reminderEnabled: Boolean? = null
    ) {
        val existing = reminderDao.getReminderState(req.patientId, req.originalVisitId, req.vaccineName)
        val oldStatus = existing?.status
        val oldDate = existing?.dueDate
        
        val reminder = existing?.copy(
            status = newStatus.name,
            dueDate = newDate ?: existing.dueDate,
            completed = (newStatus == ReminderStatus.COMPLETED || newStatus == ReminderStatus.DISMISSED || newStatus == ReminderStatus.EXTERNAL),
            priority = priority ?: existing.priority,
            reminderEnabled = reminderEnabled ?: existing.reminderEnabled,
            updatedAt = System.currentTimeMillis(),
            isSynced = false
        ) ?: ReminderEntity(
            patientId = req.patientId,
            originalVisitId = req.originalVisitId,
            vaccineName = req.vaccineName,
            dueDate = newDate ?: PatientUtils.formatDate(req.dueDate),
            status = newStatus.name,
            priority = priority ?: "NORMAL",
            reminderEnabled = reminderEnabled ?: true,
            completed = (newStatus == ReminderStatus.COMPLETED || newStatus == ReminderStatus.DISMISSED || newStatus == ReminderStatus.EXTERNAL),
            isSynced = false
        )
        
        val id = reminderDao.insertOrUpdate(reminder)

        val auditId = reminderAuditDao.insertAudit(
            ReminderAuditEntity(
                patientId = req.patientId,
                originalVisitId = req.originalVisitId,
                vaccineName = req.vaccineName,
                action = newStatus.name,
                oldStatus = oldStatus,
                newStatus = newStatus.name,
                oldDate = oldDate,
                newDate = newDate,
                performedBy = performedBy,
                priority = priority ?: reminder.priority,
                reminderEnabled = reminderEnabled ?: reminder.reminderEnabled,
                reason = reason,
                notes = notes,
                isSynced = false
            )
        )
        
        syncRepository.enqueue("REMINDER_OVERRIDE", id.toString(), SyncOperation.UPDATE, SyncPriority.MEDIUM)
        syncRepository.enqueue("REMINDER_AUDIT", auditId.toString(), SyncOperation.CREATE, SyncPriority.LOW)
    }

    override suspend fun syncWithRemote() {
        // Now handled via SyncRepository and SyncWorker
    }

    override suspend fun markCompleted(id: Long, timestamp: Long) {
        withContext(Dispatchers.IO) {
            val existing = reminderDao.getReminderById(id)
            if (existing != null) {
                reminderDao.updateStatus(id, ReminderStatus.COMPLETED.name, true, timestamp)
                
                val auditId = reminderAuditDao.insertAudit(ReminderAuditEntity(
                    patientId = existing.patientId,
                    originalVisitId = existing.originalVisitId,
                    vaccineName = existing.vaccineName,
                    action = "COMPLETED",
                    oldStatus = existing.status,
                    newStatus = ReminderStatus.COMPLETED.name,
                    oldDate = existing.dueDate,
                    newDate = existing.dueDate,
                    priority = existing.priority,
                    reminderEnabled = existing.reminderEnabled,
                    performedBy = "SYSTEM_NOTIFICATION",
                    notes = "Marked completed via notification action",
                    isSynced = false
                ))
                
                syncRepository.enqueue("REMINDER_OVERRIDE", id.toString(), SyncOperation.UPDATE, SyncPriority.MEDIUM)
                syncRepository.enqueue("REMINDER_AUDIT", auditId.toString(), SyncOperation.CREATE, SyncPriority.LOW)
            }
        }
    }

    override suspend fun insertReminder(reminder: ReminderEntity): Long {
        val id = reminderDao.insertOrUpdate(reminder)
        syncRepository.enqueue("REMINDER_OVERRIDE", id.toString(), SyncOperation.CREATE, SyncPriority.MEDIUM)
        return id
    }

    override fun triggerImmediateCheck() {
        reminderScheduler.runNow()
        WidgetUtils.updateWidget(context)
    }
}
