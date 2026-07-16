package com.clinic.neochild.data.repository

import android.content.Context
import com.clinic.neochild.data.local.dao.ReminderDao
import com.clinic.neochild.data.local.dao.VaccinationDao
import com.clinic.neochild.data.local.dao.VaccineDao
import com.clinic.neochild.data.local.entity.ReminderEntity
import com.clinic.neochild.data.local.entity.toEntity
import com.clinic.neochild.data.local.entity.toVaccination
import com.clinic.neochild.data.local.entity.toVaccine
import com.clinic.neochild.data.model.ReminderStatus
import com.clinic.neochild.data.model.ReminderType
import com.clinic.neochild.data.model.Vaccination
import com.clinic.neochild.data.model.VaccinationSource
import com.clinic.neochild.domain.repository.ReminderRepository
import com.clinic.neochild.notification.ReminderScheduler
import com.clinic.neochild.utils.PatientUtils
import com.clinic.neochild.utils.PendingRequirement
import com.clinic.neochild.utils.ReminderEngine
import com.clinic.neochild.utils.WidgetUtils
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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderRepositoryImpl @Inject constructor(
    private val reminderDao: ReminderDao,
    private val vaccinationDao: VaccinationDao,
    private val vaccineDao: VaccineDao,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val reminderScheduler: ReminderScheduler,
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

    override suspend fun markAsDone(requirement: PendingRequirement) = withContext(Dispatchers.IO) {
        val user = auth.currentUser?.email ?: "Unknown"
        
        // 1. Create a new Vaccination record
        val newVaccination = Vaccination(
            id = UUID.randomUUID().toString(),
            patientId = requirement.patientId,
            vaccineNames = listOf(requirement.vaccineName),
            dateGiven = PatientUtils.formatDate(java.util.Date()),
            isDone = true,
            source = VaccinationSource.CLINIC.name,
            performedBy = user
        )
        
        vaccinationDao.insertVaccination(newVaccination.toEntity(isSynced = false))
        
        // 2. Update Inventory (Deduct stock)
        try {
            val vaccines = vaccineDao.getAllVaccines().first()
            val match = vaccines.find { it.brandName.contains(requirement.vaccineName, ignoreCase = true) }
            if (match != null && match.stock > 0) {
                vaccineDao.insertVaccine(match.copy(stock = match.stock - 1, isSynced = false))
            }
        } catch (e: Exception) {}

        // 3. Update Reminder Status
        updateReminderStatus(requirement, ReminderStatus.COMPLETED)
        
        // 4. Remote Sync
        try {
            firestore.collection("vaccinations").document(newVaccination.id).set(newVaccination).await()
            vaccinationDao.insertVaccination(newVaccination.toEntity(isSynced = true))
        } catch (e: Exception) {}

        triggerImmediateCheck()
    }

    override suspend fun reschedule(requirement: PendingRequirement, newDate: String, reason: String) = withContext(Dispatchers.IO) {
        val existing = reminderDao.getReminder(requirement.patientId, requirement.originalVisitId, requirement.vaccineName)
        
        val reminder = existing?.copy(
            status = ReminderStatus.RESCHEDULED.name,
            dueDate = newDate,
            updatedAt = System.currentTimeMillis()
        ) ?: ReminderEntity(
            patientId = requirement.patientId,
            originalVisitId = requirement.originalVisitId,
            vaccineName = requirement.vaccineName,
            dueDate = newDate,
            status = ReminderStatus.RESCHEDULED.name,
            updatedAt = System.currentTimeMillis()
        )
        
        reminderDao.insertReminder(reminder)
        triggerImmediateCheck()
    }

    override suspend fun markVaccinatedElsewhere(
        requirement: PendingRequirement,
        source: VaccinationSource,
        date: String,
        notes: String
    ) = withContext(Dispatchers.IO) {
        val user = auth.currentUser?.email ?: "Unknown"
        
        val newVaccination = Vaccination(
            id = UUID.randomUUID().toString(),
            patientId = requirement.patientId,
            vaccineNames = listOf(requirement.vaccineName),
            dateGiven = date,
            isDone = true,
            source = source.name,
            notes = notes,
            performedBy = user
        )

        vaccinationDao.insertVaccination(newVaccination.toEntity(isSynced = false))
        updateReminderStatus(requirement, ReminderStatus.EXTERNAL, source.name)

        try {
            firestore.collection("vaccinations").document(newVaccination.id).set(newVaccination).await()
            vaccinationDao.insertVaccination(newVaccination.toEntity(isSynced = true))
        } catch (e: Exception) {}

        triggerImmediateCheck()
    }

    override suspend fun dismissReminder(requirement: PendingRequirement) = withContext(Dispatchers.IO) {
        updateReminderStatus(requirement, ReminderStatus.DISMISSED)
        triggerImmediateCheck()
    }

    private suspend fun updateReminderStatus(
        req: PendingRequirement, 
        status: ReminderStatus, 
        source: String? = null
    ) {
        val existing = reminderDao.getReminder(req.patientId, req.originalVisitId, req.vaccineName)
        val reminder = existing?.copy(
            status = status.name,
            completed = (status == ReminderStatus.COMPLETED || status == ReminderStatus.DISMISSED || status == ReminderStatus.EXTERNAL),
            vaccinationSource = source,
            updatedAt = System.currentTimeMillis()
        ) ?: ReminderEntity(
            patientId = req.patientId,
            originalVisitId = req.originalVisitId,
            vaccineName = req.vaccineName,
            dueDate = PatientUtils.formatDate(req.dueDate),
            status = status.name,
            completed = (status == ReminderStatus.COMPLETED || status == ReminderStatus.DISMISSED || status == ReminderStatus.EXTERNAL),
            vaccinationSource = source,
            updatedAt = System.currentTimeMillis()
        )
        reminderDao.insertReminder(reminder)
    }

    override suspend fun markCompleted(id: Long, timestamp: Long) {
        reminderDao.markCompleted(id, timestamp)
    }

    override suspend fun markPatientRemindersCompleted(patientId: String, timestamp: Long) {
        reminderDao.markPatientRemindersCompleted(patientId, timestamp)
    }

    override suspend fun getPendingPatientReminder(patientId: String, type: ReminderType): ReminderEntity? {
        return reminderDao.getPendingPatientReminder(patientId, type.name)
    }

    override suspend fun getPendingVaccineReminder(vaccineId: String, type: ReminderType): ReminderEntity? {
        return reminderDao.getPendingVaccineReminder(vaccineId, type.name)
    }

    override suspend fun insertReminder(reminder: ReminderEntity): Long {
        return reminderDao.insertReminder(reminder)
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
