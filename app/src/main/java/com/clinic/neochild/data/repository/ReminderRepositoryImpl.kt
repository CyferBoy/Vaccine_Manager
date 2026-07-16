package com.clinic.neochild.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.clinic.neochild.data.local.AppDatabase
import com.clinic.neochild.data.local.dao.*
import com.clinic.neochild.data.local.entity.*
import com.clinic.neochild.data.model.*
import com.clinic.neochild.domain.repository.ReminderRepository
import com.clinic.neochild.notification.ReminderScheduler
import com.clinic.neochild.utils.*
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
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val reminderScheduler: ReminderScheduler,
    private val notificationHelper: com.clinic.neochild.notification.NotificationHelper,
    @ApplicationContext private val context: Context
) : ReminderRepository {

    override fun getUnsatisfiedRequirements(): Flow<List<PendingRequirement>> {
        return vaccinationDao.getAllVaccinations().map { entities ->
            val vaccinations = entities.map { it.toVaccination() }
            ReminderEngine.getPotentialRequirements(vaccinations)
        }
    }

    override fun getAllReminderHistory(): Flow<List<ReminderEntity>> {
        return reminderDao.getAllReminders()
    }

    override fun getDueList(): Flow<List<Vaccination>> {
        return combine(
            vaccinationDao.getAllVaccinations(),
            reminderDao.getAllReminders()
        ) { vaccEntities, reminderEntities ->
            val allVaccinations = vaccEntities.map { it.toVaccination() }
            val potential = ReminderEngine.getPotentialRequirements(allVaccinations)
            val reminderMap = reminderEntities.associateBy { "${it.patientId}_${it.originalVisitId}_${it.vaccineName}" }

            potential.mapNotNull { req ->
                val key = "${req.patientId}_${req.originalVisitId}_${req.vaccineName}"
                val savedReminder = reminderMap[key]
                
                val status = savedReminder?.status?.let { ReminderStatus.valueOf(it) } ?: ReminderStatus.ACTIVE
                
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

    override fun getDueToday(): Flow<List<Vaccination>> {
        return getDueList().map { list ->
            PatientUtils.filterVaccinationsByPeriod(list, "Today")
        }
    }

    override fun getDueTomorrow(): Flow<List<Vaccination>> {
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }.time
        val tomorrowStr = PatientUtils.formatDate(tomorrow)
        return getDueList().map { list ->
            list.filter { it.nextDueDate == tomorrowStr }
        }
    }

    override fun getOverdue(): Flow<List<Vaccination>> {
        return getDueList().map { list ->
            PatientUtils.filterVaccinationsByPeriod(list, "Overdue")
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
            cancelVaccinationNotifications(requirement.patientId)
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
            cancelVaccinationNotifications(requirement.patientId)
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
            cancelVaccinationNotifications(requirement.patientId)
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
            cancelVaccinationNotifications(requirement.patientId)
            triggerImmediateCheck()
            syncWithRemote()
        }
    }

    private fun cancelVaccinationNotifications(patientId: String) {
        ReminderType.entries.forEach { type ->
            notificationHelper.cancelNotification(patientId.hashCode() + type.ordinal)
        }
    }

    override suspend fun undoAction(auditId: Long, performedBy: String) {
        withContext(Dispatchers.IO) {
            // Implementation
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
        reminderDao.markCompleted(id, timestamp)
    }

    override suspend fun markPatientRemindersCompleted(patientId: String, timestamp: Long) {
        // This is now handled more granularly by the status system
    }

    override suspend fun getPendingPatientReminder(patientId: String, type: ReminderType): ReminderEntity? {
        return reminderDao.getPendingPatientReminder(patientId, type.name)
    }

    override suspend fun getPendingVaccineReminder(vaccineId: String, type: ReminderType): ReminderEntity? {
        return reminderDao.getPendingVaccineReminder(vaccineId, type.name)
    }

    override suspend fun insertReminder(reminder: ReminderEntity): Long {
        return reminderDao.insertOrUpdate(reminder)
    }

    override suspend fun deleteOldReminders(days: Int) {
        val timestamp = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
        reminderDao.deleteOldCompletedReminders(timestamp)
    }

    override fun triggerImmediateCheck() {
        reminderScheduler.runNow()
        WidgetUtils.updateWidget(context)
    }
}
