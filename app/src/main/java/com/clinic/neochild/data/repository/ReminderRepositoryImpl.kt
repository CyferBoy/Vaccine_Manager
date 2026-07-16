package com.clinic.neochild.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.clinic.neochild.data.local.AppDatabase
import com.clinic.neochild.data.local.dao.*
import com.clinic.neochild.data.local.entity.*
import com.clinic.neochild.data.model.*
import com.clinic.neochild.domain.repository.ReminderRepository
import com.clinic.neochild.domain.repository.ReminderStats
import com.clinic.neochild.notification.ReminderScheduler
import com.clinic.neochild.core.utils.*
import com.clinic.neochild.domain.logic.ReminderEngine
import com.clinic.neochild.domain.model.PendingRequirement
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val reminderDao: ReminderDao,
    private val reminderAuditDao: ReminderAuditDao,
    private val vaccinationDao: VaccinationDao,
    private val vaccineDao: VaccineDao,
    private val patientDao: com.clinic.neochild.data.local.dao.PatientDao,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val reminderScheduler: ReminderScheduler,
    private val notificationHelper: com.clinic.neochild.notification.NotificationHelper,
    @ApplicationContext private val context: Context
) : ReminderRepository {

    override fun getDueList(
        searchQuery: String,
        filterStatus: List<ReminderStatus>?
    ): Flow<List<Vaccination>> {
        return combine(
            vaccinationDao.getAllVaccinations(),
            reminderDao.getAllReminders(),
            patientDao.getAllPatients()
        ) { vaccEntities, reminderEntities, patientEntities ->
            val allVaccinations = vaccEntities.map { it.toVaccination() }
            val potential = ReminderEngine.getPotentialRequirements(allVaccinations)
            val reminderMap = reminderEntities.associateBy { "${it.patientId}_${it.originalVisitId}_${it.vaccineName}" }
            val patientMap = patientEntities.associateBy { it.id }

            potential.mapNotNull { req ->
                val key = "${req.patientId}_${req.originalVisitId}_${req.vaccineName}"
                val savedReminder = reminderMap[key]
                val patient = patientMap[req.patientId]

                // Search Filter
                if (searchQuery.isNotBlank() && patient != null) {
                    val matchesName = patient.name.contains(searchQuery, ignoreCase = true)
                    val matchesPhone = patient.phone.contains(searchQuery)
                    val matchesVaccine = req.vaccineName.contains(searchQuery, ignoreCase = true)
                    if (!matchesName && !matchesPhone && !matchesVaccine) return@mapNotNull null
                }

                val status = savedReminder?.status?.let { ReminderStatus.valueOf(it) } ?: ReminderStatus.ACTIVE
                
                // Status Filter
                if (filterStatus != null && !filterStatus.contains(status)) return@mapNotNull null

                // Hidden States
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
    }

    override fun getDueToday(): Flow<List<Vaccination>> = getDueList().map { 
        PatientUtils.filterVaccinationsByPeriod(it, "Today") 
    }

    override fun getDueTomorrow(): Flow<List<Vaccination>> = getDueList().map { 
        PatientUtils.filterVaccinationsByPeriod(it, "This Week").filter { v ->
            v.nextDueDate == PatientUtils.formatDate(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }.time)
        }
    }

    override fun getOverdue(): Flow<List<Vaccination>> = getDueList().map { 
        PatientUtils.filterVaccinationsByPeriod(it, "Overdue") 
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
                    reminderDao.insertOrUpdate(reminder)
                    
                    reminderAuditDao.insertAudit(ReminderAuditEntity(
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
                        notes = "Staff scheduled follow-up"
                    ))
                }
            }
            if (reminderEnabled) triggerImmediateCheck()
            syncWithRemote()
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
            syncWithRemote()
        }
    }

    override suspend fun deleteReminder(requirement: PendingRequirement, performedBy: String) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                reminderDao.deleteReminder(requirement.patientId, requirement.originalVisitId, requirement.vaccineName)
                reminderAuditDao.insertAudit(ReminderAuditEntity(
                    patientId = requirement.patientId,
                    originalVisitId = requirement.originalVisitId,
                    vaccineName = requirement.vaccineName,
                    action = "DELETED",
                    oldStatus = null,
                    newStatus = "DELETED",
                    oldDate = null,
                    newDate = null,
                    priority = null,
                    reminderEnabled = null,
                    performedBy = performedBy,
                    notes = "Deleted by admin"
                ))
            }
            triggerImmediateCheck()
            syncWithRemote()
        }
    }

    override fun getPatientFollowUps(patientId: String): Flow<List<ReminderEntity>> {
        return reminderDao.getFollowUpsForPatient(patientId)
    }

    override fun getDashboardStats(): Flow<ReminderStats> {
        return combine(
            getDueToday(),
            getDueTomorrow(),
            getOverdue(),
            reminderDao.getAllReminders()
        ) { today, tomorrow, overdue, allReminders ->
            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            
            ReminderStats(
                dueToday = today.size,
                dueTomorrow = tomorrow.size,
                overdue = overdue.size,
                completedToday = allReminders.count { it.status == ReminderStatus.COMPLETED.name && it.updatedAt >= todayStart },
                rescheduledToday = allReminders.count { it.status == ReminderStatus.RESCHEDULED.name && it.updatedAt >= todayStart },
                externalToday = allReminders.count { it.status == ReminderStatus.EXTERNAL.name && it.updatedAt >= todayStart },
                notificationsSentToday = allReminders.count { it.notificationSent && it.lastReminderTime >= todayStart }
            )
        }
    }

    override suspend fun markAsDone(
        requirement: PendingRequirement,
        performedBy: String
    ) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val newVaccination = Vaccination(
                    id = UUID.randomUUID().toString(),
                    patientId = requirement.patientId,
                    vaccineNames = listOf(requirement.vaccineName),
                    dateGiven = PatientUtils.formatDate(java.util.Date()),
                    isDone = true,
                    source = VaccinationSource.CLINIC.name,
                    performedBy = performedBy
                )
                vaccinationDao.insertVaccination(newVaccination.toEntity(isSynced = false))

                val vaccines = vaccineDao.getAllVaccines().first()
                val match = vaccines.find { 
                    it.brandName.contains(requirement.vaccineName, ignoreCase = true) && it.stock > 0 
                }
                if (match != null) {
                    vaccineDao.insertVaccine(match.copy(stock = match.stock - 1, isSynced = false))
                }

                updateReminderStateAndAudit(
                    req = requirement,
                    newStatus = ReminderStatus.COMPLETED,
                    performedBy = performedBy,
                    notes = "Vaccinated in clinic"
                )
            }
            triggerImmediateCheck()
            syncWithRemote()
        }
    }

    override suspend fun reschedule(
        requirement: PendingRequirement,
        newDate: String,
        reason: String,
        performedBy: String
    ) {
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
            syncWithRemote()
        }
    }

    override suspend fun markVaccinatedElsewhere(
        requirement: PendingRequirement,
        source: VaccinationSource,
        date: String,
        notes: String,
        performedBy: String
    ) {
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

                updateReminderStateAndAudit(
                    req = requirement,
                    newStatus = ReminderStatus.EXTERNAL,
                    performedBy = performedBy,
                    notes = "Given at: ${source.name}. Notes: $notes"
                )
            }
            triggerImmediateCheck()
            syncWithRemote()
        }
    }

    override suspend fun dismissReminder(
        requirement: PendingRequirement,
        reason: String,
        performedBy: String
    ) {
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
            syncWithRemote()
        }
    }

    override suspend fun undoAction(auditId: Long, performedBy: String) {
        withContext(Dispatchers.IO) {
            // Future medical correction workflow
        }
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
        notes: String? = null
    ) {
        val existing = reminderDao.getReminderState(req.patientId, req.originalVisitId, req.vaccineName)
        val oldStatus = existing?.status
        val oldDate = existing?.dueDate
        
        val reminder = existing?.copy(
            status = newStatus.name,
            dueDate = newDate ?: existing.dueDate,
            completed = (newStatus == ReminderStatus.COMPLETED || newStatus == ReminderStatus.DISMISSED || newStatus == ReminderStatus.EXTERNAL),
            updatedAt = System.currentTimeMillis(),
            isSynced = false
        ) ?: ReminderEntity(
            patientId = req.patientId,
            originalVisitId = req.originalVisitId,
            vaccineName = req.vaccineName,
            dueDate = newDate ?: PatientUtils.formatDate(req.dueDate),
            status = newStatus.name,
            completed = (newStatus == ReminderStatus.COMPLETED || newStatus == ReminderStatus.DISMISSED || newStatus == ReminderStatus.EXTERNAL),
            isSynced = false
        )
        
        reminderDao.insertOrUpdate(reminder)

        reminderAuditDao.insertAudit(
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
                reason = reason,
                notes = notes,
                isSynced = false
            )
        )
    }

    override suspend fun syncWithRemote() {
        withContext(Dispatchers.IO) {
            try {
                val unsyncedAudits = reminderAuditDao.getUnsyncedAudits()
                for (audit in unsyncedAudits) {
                    firestore.collection("reminder_audits").document(audit.auditId.toString()).set(audit).await()
                    reminderAuditDao.markSynced(audit.auditId)
                }

                val unsyncedReminders = reminderDao.getUnsyncedReminders()
                for (reminder in unsyncedReminders) {
                    firestore.collection("reminder_overrides").document(reminder.id.toString()).set(reminder).await()
                    reminderDao.markSynced(reminder.id)
                }
            } catch (e: Exception) {
                android.util.Log.e("ReminderSync", "Remote sync failed", e)
            }
        }
    }

    override suspend fun markCompleted(id: Long, timestamp: Long) {
        reminderDao.updateStatus(id, ReminderStatus.COMPLETED.name, true, timestamp)
    }

    override suspend fun markPatientRemindersCompleted(patientId: String, timestamp: Long) {
        reminderDao.markAllForPatientCompleted(patientId, timestamp)
    }

    override suspend fun getPendingPatientReminder(patientId: String, type: ReminderType): ReminderEntity? = null

    override suspend fun getPendingVaccineReminder(vaccineId: String, type: ReminderType): ReminderEntity? = null

    override suspend fun insertReminder(reminder: ReminderEntity): Long {
        return reminderDao.insertOrUpdate(reminder)
    }

    override suspend fun deleteOldReminders(days: Int) {}

    override fun triggerImmediateCheck() {
        reminderScheduler.runNow()
        WidgetUtils.updateWidget(context)
    }
}
