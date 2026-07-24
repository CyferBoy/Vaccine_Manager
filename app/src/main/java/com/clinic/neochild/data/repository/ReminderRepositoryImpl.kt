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
import com.clinic.neochild.data.remote.mapper.FirestoreMappers
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
    private val firestore: FirebaseFirestore,
    private val reminderDao: ReminderDao,
    private val dueReminderDao: DueReminderDao,
    private val vaccinationDao: VaccinationDao,
    private val patientDao: PatientDao,
    private val auditLogDao: AuditLogDao,
    private val syncRepository: SyncRepository,
    private val reminderScheduler: ReminderScheduler,
    private val auditLogger: AuditLogger,
    @ApplicationContext private val context: Context
) : ReminderRepository {

    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun logReminderUndoableChange(
        reminder: ReminderEntity?,
        action: String,
        remarks: String? = null,
        newValue: String? = null,
        explicitEntityId: String? = null
    ) {
        val oldValueJson = reminder?.let { json.encodeToString(it) }
        val entityId = explicitEntityId ?: if (reminder != null) {
            "${reminder.patientId}||${reminder.originalVisitId}||${reminder.vaccineName}"
        } else "UNKNOWN"
        
        auditLogger.recordLog(
            module = "PATIENT",
            entityType = "REMINDER",
            entityId = entityId,
            patientId = reminder?.patientId,
            action = action,
            oldValue = oldValueJson,
            newValue = newValue,
            remarks = remarks
        )
    }

    /**
     * Shared logic to combine raw entities into a processed list of vaccinations.
     * Uses the unified reminder_states table.
     */
    private fun getProcessedDueFlow(): Flow<Pair<List<Vaccination>, List<PatientEntity>>> {
        return combine(
            vaccinationDao.getAllVaccinations(),
            dueReminderDao.getAllReminders(),
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
        
        val stateFiltered = if (filterStatus == null || filterStatus.isEmpty()) {
            processed.filter { it.status == ReminderStatus.ACTIVE || it.status == ReminderStatus.RESCHEDULED }
        } else {
            processed.filter { filterStatus.contains(it.status) }
        }

        stateFiltered.filter { vacc ->
            val patient = patientMap[vacc.patientId]
            val matchesSearch = if (searchQuery.isBlank()) true else {
                patient?.name?.contains(searchQuery, ignoreCase = true) == true ||
                patient?.phone?.contains(searchQuery) == true ||
                vacc.nxtVaccineNames.any { it.contains(searchQuery, ignoreCase = true) }
            }
            matchesSearch
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
        dueReminderDao.getAllReminders(),
        patientDao.getAllPatients()
    ) { vaccs, reminders, _ ->
        val dueList = processDueListInternal(vaccs, reminders)
        
        val todayCal = DateClassifier.getTodayStart()
        val todayStart = todayCal.timeInMillis

        ReminderStats(
            dueToday = dueList.count { 
                val cat = DateClassifier.classify(it.nextDueDate, todayCal)
                cat is DateCategory.Today || cat is DateCategory.GracePeriod || cat is DateCategory.Yesterday
            },
            dueTomorrow = dueList.count { DateClassifier.classify(it.nextDueDate, todayCal) is DateCategory.Tomorrow },
            overdue = dueList.count { 
                val cat = DateClassifier.classify(it.nextDueDate, todayCal)
                cat is DateCategory.Overdue || cat is DateCategory.Yesterday
            },
            completedToday = reminders.count { it.status == "COMPLETED" && (it.completionDate ?: 0) >= todayStart },
            rescheduledToday = reminders.count { it.status == "RESCHEDULED" && it.updatedAt >= todayStart },
            externalToday = reminders.count { it.status == "EXTERNAL" && it.updatedAt >= todayStart },
            notificationsSentToday = reminders.count { it.notificationSent && it.lastReminderTime >= todayStart }
        )
    }

    private fun processDueListInternal(
        vaccEntities: List<VisitEntity>,
        reminderEntities: List<ReminderEntity>
    ): List<Vaccination> {
        val allVaccinations = vaccEntities.map { it.toVaccination() }
        val potential = ReminderEngine.getPotentialRequirements(allVaccinations)
        
        val reminderMap = reminderEntities.associateBy { "${it.patientId}_${it.originalVisitId}_${it.vaccineName}" }
        val result = mutableListOf<Vaccination>()

        // 1. Process Potential (Due/Overdue)
        potential.forEach { req ->
            val key = "${req.patientId}_${req.originalVisitId}_${req.vaccineName}"
            val savedState = reminderMap[key]
            
            // If it's already completed or dismissed elsewhere, skip adding as "potential"
            if (savedState?.status == "COMPLETED" || savedState?.status == "DISMISSED" || savedState?.status == "EXTERNAL") {
                // Will be handled in step 2/3/4
            } else {
                val status = try {
                    savedState?.status?.let { ReminderStatus.valueOf(it) } ?: ReminderStatus.ACTIVE
                } catch (_: Exception) {
                    ReminderStatus.ACTIVE
                }

                val finalDueDate = if (status == ReminderStatus.RESCHEDULED && savedState != null) {
                    savedState.dueDate
                } else {
                    PatientUtils.formatDate(req.dueDate)
                }

                allVaccinations.find { it.id == req.originalVisitId }?.copy(
                    nxtVaccineNames = listOf(req.vaccineName),
                    nextDueDate = finalDueDate,
                    isDone = false,
                    status = status,
                    performedBy = savedState?.performedBy ?: ""
                )?.let { result.add(it) }
            }
        }

        // 2. Process Terminal States (Completed, Dismissed, External)
        reminderEntities.filter { it.status != "ACTIVE" && it.status != "RESCHEDULED" }.forEach { state ->
            val vaccination = allVaccinations.find { it.id == state.originalVisitId }
            if (vaccination != null) {
                val status = try { ReminderStatus.valueOf(state.status) } catch (_: Exception) { ReminderStatus.ACTIVE }
                result.add(vaccination.copy(
                    nxtVaccineNames = listOf(state.vaccineName),
                    nextDueDate = state.dueDate,
                    isDone = status == ReminderStatus.COMPLETED || status == ReminderStatus.EXTERNAL,
                    status = status,
                    dateGiven = if (status == ReminderStatus.COMPLETED) PatientUtils.formatDate(Date(state.completionDate ?: 0)) 
                                else if (status == ReminderStatus.EXTERNAL) state.externalDate ?: "" 
                                else "",
                    performedBy = state.performedBy ?: "",
                    notes = state.notes ?: state.dismissalReason ?: ""
                ))
            }
        }

        return result
    }

    private suspend fun enqueueReminderSync(
        entityName: String,
        patientId: String,
        visitId: String,
        vaccineName: String,
        operation: SyncOperation,
        priority: SyncPriority
    ) {
        val syncId = "${patientId}||${visitId}||${vaccineName}"
        syncRepository.enqueue(entityName, syncId, operation, priority)
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
                    dueReminderDao.clearAllStates(patientId, originalVisitId, name)
                    
                    val reminder = ReminderEntity(
                        patientId = patientId,
                        originalVisitId = originalVisitId,
                        vaccineName = name,
                        dueDate = dueDate,
                        reminderDate = dueDate,
                        status = "ACTIVE",
                        priority = priority,
                        reminderEnabled = reminderEnabled,
                        notes = notes,
                        isSynced = false
                    )
                    dueReminderDao.insertReminder(reminder)
                    
                    logReminderUndoableChange(
                        reminder = null, 
                        action = "SCHEDULED",
                        remarks = "Follow-up scheduled by $performedBy",
                        newValue = dueDate,
                        explicitEntityId = "${patientId}||${originalVisitId}||${name}"
                    )

                    enqueueReminderSync("REMINDER_STATE", patientId, originalVisitId, name, SyncOperation.CREATE, SyncPriority.MEDIUM)
                }
            }
            if (reminderEnabled) triggerImmediateCheck()
        }
    }

    override suspend fun markRequirementSatisfied(requirement: PendingRequirement, performedBy: String) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val existing = dueReminderDao.getReminderByStableId(requirement.patientId, requirement.originalVisitId, requirement.vaccineName)
                
                if (existing != null) {
                    logReminderUndoableChange(existing, "COMPLETED", "${requirement.vaccineName} marked done by $performedBy")
                    dueReminderDao.moveDueToCompleted(existing, performedBy, "Requirement satisfied")
                    enqueueReminderSync("REMINDER_STATE", existing.patientId, existing.originalVisitId, existing.vaccineName, SyncOperation.UPDATE, SyncPriority.MEDIUM)
                } else {
                    val completed = ReminderEntity(
                        patientId = requirement.patientId,
                        originalVisitId = requirement.originalVisitId,
                        vaccineName = requirement.vaccineName,
                        dueDate = PatientUtils.formatDate(requirement.dueDate),
                        status = "COMPLETED",
                        completionDate = System.currentTimeMillis(),
                        performedBy = performedBy,
                        notes = "Requirement satisfied"
                    )
                    logReminderUndoableChange(null, "COMPLETED", "${requirement.vaccineName} marked done by $performedBy", explicitEntityId = "${requirement.patientId}||${requirement.originalVisitId}||${requirement.vaccineName}")
                    dueReminderDao.insertReminder(completed)
                    enqueueReminderSync("REMINDER_STATE", requirement.patientId, requirement.originalVisitId, requirement.vaccineName, SyncOperation.CREATE, SyncPriority.MEDIUM)
                }
            }
            triggerImmediateCheck()
        }
    }

    override suspend fun reschedule(requirement: PendingRequirement, newDate: String, reminderDate: String, reason: String, performedBy: String) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val existing = dueReminderDao.getReminderByStableId(requirement.patientId, requirement.originalVisitId, requirement.vaccineName)
                
                val updated = existing?.copy(
                    dueDate = newDate,
                    reminderDate = reminderDate,
                    status = "RESCHEDULED",
                    updatedAt = System.currentTimeMillis(),
                    notes = if (reason.isNotBlank()) "${existing.notes ?: ""}\nRescheduled: $reason" else existing.notes,
                    isSynced = false
                ) ?: ReminderEntity(
                    patientId = requirement.patientId,
                    originalVisitId = requirement.originalVisitId,
                    vaccineName = requirement.vaccineName,
                    dueDate = newDate,
                    reminderDate = reminderDate,
                    status = "RESCHEDULED",
                    notes = reason,
                    isSynced = false
                )

                dueReminderDao.insertReminder(updated)

                logReminderUndoableChange(
                    reminder = existing, 
                    action = "RESCHEDULED",
                    remarks = "Rescheduled: $reason",
                    newValue = newDate,
                    explicitEntityId = "${requirement.patientId}||${requirement.originalVisitId}||${requirement.vaccineName}"
                )
                
                enqueueReminderSync("REMINDER_STATE", requirement.patientId, requirement.originalVisitId, requirement.vaccineName, SyncOperation.UPDATE, SyncPriority.MEDIUM)
            }
            triggerImmediateCheck()
        }
    }

    override suspend fun markVaccinatedElsewhere(requirement: PendingRequirement, source: VaccinationSource, date: String, notes: String, performedBy: String) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                // 1. Record Visit (Vaccinated elsewhere is still a visit record)
                val visit = VisitEntity(
                    id = UUID.randomUUID().toString(),
                    patientId = requirement.patientId,
                    dateGiven = date,
                    doctor = performedBy,
                    vaccineNames = requirement.vaccineName,
                    notes = notes,
                    source = source.name,
                    isDone = true
                )
                vaccinationDao.insertVaccination(visit)
                syncRepository.enqueue("VACCINATION", visit.id, SyncOperation.CREATE, SyncPriority.MEDIUM)

                // 2. Update Reminder State
                val existing = dueReminderDao.getReminderByStableId(requirement.patientId, requirement.originalVisitId, requirement.vaccineName)
                
                logReminderUndoableChange(
                    reminder = existing,
                    action = "EXTERNAL",
                    remarks = "Vaccinated elsewhere: ${source.name}",
                    explicitEntityId = "${requirement.patientId}||${requirement.originalVisitId}||${requirement.vaccineName}"
                )

                if (existing != null) {
                    dueReminderDao.moveDueToExternal(existing, source.name, date, performedBy, notes)
                } else {
                    val external = ReminderEntity(
                        patientId = requirement.patientId,
                        originalVisitId = requirement.originalVisitId,
                        vaccineName = requirement.vaccineName,
                        dueDate = PatientUtils.formatDate(requirement.dueDate),
                        status = "EXTERNAL",
                        externalDate = date,
                        source = source.name,
                        performedBy = performedBy,
                        notes = notes
                    )
                    dueReminderDao.insertReminder(external)
                }
                
                enqueueReminderSync("REMINDER_STATE", requirement.patientId, requirement.originalVisitId, requirement.vaccineName, SyncOperation.UPDATE, SyncPriority.MEDIUM)
            }
            triggerImmediateCheck()
        }
    }

    override suspend fun dismissReminder(requirement: PendingRequirement, reason: String, performedBy: String) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val existing = dueReminderDao.getReminderByStableId(requirement.patientId, requirement.originalVisitId, requirement.vaccineName)
                
                logReminderUndoableChange(
                    reminder = existing,
                    action = "DISMISSED",
                    remarks = "Dismissed: $reason",
                    explicitEntityId = "${requirement.patientId}||${requirement.originalVisitId}||${requirement.vaccineName}"
                )

                if (existing != null) {
                    dueReminderDao.moveDueToDismissed(existing, performedBy, reason)
                } else {
                    val dismissed = ReminderEntity(
                        patientId = requirement.patientId,
                        originalVisitId = requirement.originalVisitId,
                        vaccineName = requirement.vaccineName,
                        dueDate = PatientUtils.formatDate(requirement.dueDate),
                        status = "DISMISSED",
                        dismissalDate = System.currentTimeMillis(),
                        performedBy = performedBy,
                        dismissalReason = reason
                    )
                    dueReminderDao.insertReminder(dismissed)
                }

                auditLogger.recordLog(
                    module = "PATIENT",
                    entityType = "REMINDER",
                    entityId = "${requirement.patientId}||${requirement.originalVisitId}||${requirement.vaccineName}",
                    action = "DISMISSED",
                    patientId = requirement.patientId,
                    remarks = "Dismissed: $reason"
                )
                
                enqueueReminderSync("REMINDER_STATE", requirement.patientId, requirement.originalVisitId, requirement.vaccineName, SyncOperation.UPDATE, SyncPriority.MEDIUM)
            }
            triggerImmediateCheck()
        }
    }

    override suspend fun restoreReminder(requirement: PendingRequirement, performedBy: String) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val existing = dueReminderDao.getReminderByStableId(requirement.patientId, requirement.originalVisitId, requirement.vaccineName)
                if (existing != null) {
                    logReminderUndoableChange(existing, "RESTORED", explicitEntityId = "${requirement.patientId}||${requirement.originalVisitId}||${requirement.vaccineName}")
                    val restored = existing.copy(
                        status = "ACTIVE",
                        updatedAt = System.currentTimeMillis(),
                        isSynced = false
                    )
                    dueReminderDao.insertReminder(restored)

                    enqueueReminderSync("REMINDER_STATE", requirement.patientId, requirement.originalVisitId, requirement.vaccineName, SyncOperation.UPDATE, SyncPriority.MEDIUM)
                }
            }
            triggerImmediateCheck()
        }
    }

    override suspend fun deleteReminder(requirement: PendingRequirement, performedBy: String) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val existing = dueReminderDao.getReminderByStableId(requirement.patientId, requirement.originalVisitId, requirement.vaccineName)
                if (existing != null) {
                    logReminderUndoableChange(existing, "DELETED", explicitEntityId = "${requirement.patientId}||${requirement.originalVisitId}||${requirement.vaccineName}")
                    dueReminderDao.softDeleteReminder(existing.id)
                    
                    enqueueReminderSync("REMINDER_STATE", existing.patientId, existing.originalVisitId, existing.vaccineName, SyncOperation.UPDATE, SyncPriority.LOW)
                }
            }
            triggerImmediateCheck()
        }
    }

    override fun getPatientFollowUps(patientId: String): Flow<List<ReminderEntity>> {
        return dueReminderDao.getDueRemindersForPatient(patientId)
    }

    override suspend fun undoAction(auditId: Long, performedBy: String) {
        withContext(Dispatchers.IO) {
            val log = auditLogDao.getLogById(auditId) ?: return@withContext
            if (log.entityType != "REMINDER") return@withContext

            database.withTransaction {
                try {
                    val previousState = log.oldValue?.let { json.decodeFromString<ReminderEntity>(it) }
                    
                    if (previousState != null) {
                        // Restore state from snapshot
                        dueReminderDao.insertReminder(previousState.copy(isSynced = false))
                        
                        auditLogger.recordLog(
                            module = "PATIENT",
                            entityType = "REMINDER",
                            entityId = log.entityId,
                            action = "UNDO",
                            patientId = log.patientId,
                            remarks = "Undid action: ${log.action} by $performedBy"
                        )
                        
                        val parts = log.entityId.split("||")
                        if (parts.size == 3) {
                            enqueueReminderSync("REMINDER_STATE", parts[0], parts[1], parts[2], SyncOperation.UPDATE, SyncPriority.HIGH)
                        }
                    } else if (log.action == "SCHEDULED") {
                        // Undoing a creation means deletion
                        val parts = log.entityId.split("||")
                        if (parts.size == 3) {
                            dueReminderDao.deleteReminder(parts[0], parts[1], parts[2])
                            enqueueReminderSync("REMINDER_STATE", parts[0], parts[1], parts[2], SyncOperation.DELETE, SyncPriority.HIGH)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ReminderRepo", "Undo failed", e)
                }
            }
            triggerImmediateCheck()
        }
    }

    override fun getAuditTrail(patientId: String): Flow<List<ReminderAuditEntity>> {
        return auditLogDao.getLogsForPatient(patientId).map { logs ->
            logs.map { log ->
                ReminderAuditEntity(
                    patientId = log.patientId ?: "",
                    originalVisitId = log.entityId,
                    vaccineName = log.remarks ?: "",
                    action = log.action,
                    oldStatus = log.oldValue,
                    newStatus = log.newValue ?: "",
                    oldDate = null,
                    newDate = log.newValue,
                    priority = null,
                    reminderEnabled = null,
                    performedBy = log.user,
                    timestamp = log.timestamp,
                    notes = log.remarks,
                    isSynced = log.isSynced
                )
            }
        }
    }

    override suspend fun refreshReminders() {
        withContext(Dispatchers.IO) {
            try {
                val snap = firestore.collection("reminders").get().await()
                val entities = snap.documents.mapNotNull { 
                    FirestoreMappers.toReminderEntity(it)
                }
                database.withTransaction {
                    for (remote in entities) {
                        // Check if we have a local version and if it's unsynced
                        val local = dueReminderDao.getReminderByStableId(
                            remote.patientId, 
                            remote.originalVisitId, 
                            remote.vaccineName
                        )
                        
                        if (local == null || local.isSynced) {
                            // Safe to overwrite or insert
                            // We preserve the local autoincrement id if it exists to avoid row replacement
                            val toSave = remote.copy(
                                id = local?.id ?: 0L,
                                isSynced = true
                            )
                            dueReminderDao.insertReminder(toSave)
                        } else {
                            // Local has unsynced changes, keep it for now
                            // The sync engine will eventually push local changes to Firestore
                        }
                    }
                }
                android.util.Log.d("ReminderRepo", "Refreshed ${entities.size} reminders")
            } catch (e: Exception) {
                android.util.Log.e("ReminderRepo", "Refresh failed", e)
            }
        }
    }

    override suspend fun markCompleted(id: Long, timestamp: Long) {
        withContext(Dispatchers.IO) {
            val existing = dueReminderDao.getReminderById(id)
            if (existing != null) {
                logReminderUndoableChange(existing, "COMPLETED", "Marked done via notification", explicitEntityId = "${existing.patientId}||${existing.originalVisitId}||${existing.vaccineName}")
                markRequirementSatisfied(
                    PendingRequirement(existing.patientId, existing.vaccineName, PatientUtils.parseDate(existing.dueDate) ?: Date(), existing.originalVisitId),
                    "SYSTEM_NOTIFICATION"
                )
            }
        }
    }

    override suspend fun insertReminder(reminder: ReminderEntity): Long {
        return dueReminderDao.insertReminder(reminder)
    }

    override suspend fun transferReminders(duplicateId: String, masterId: String) {
        dueReminderDao.updatePatientId(duplicateId, masterId)
    }

    override fun triggerImmediateCheck() {
        reminderScheduler.runNow()
        WidgetUtils.updateWidget(context)
    }
}
