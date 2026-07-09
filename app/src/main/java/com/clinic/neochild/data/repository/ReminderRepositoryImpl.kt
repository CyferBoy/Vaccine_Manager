package com.clinic.neochild.data.repository

import android.content.Context
import com.clinic.neochild.data.local.dao.ReminderDao
import com.clinic.neochild.data.local.dao.VaccinationDao
import com.clinic.neochild.data.local.dao.VaccineDao
import com.clinic.neochild.data.local.entity.ReminderEntity
import com.clinic.neochild.data.local.entity.toEntity
import com.clinic.neochild.data.local.entity.toVaccination
import com.clinic.neochild.data.local.entity.toVaccine
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
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val reminderScheduler: ReminderScheduler,
    @ApplicationContext private val context: Context
) : ReminderRepository {

    override fun getUnsatisfiedRequirements(): Flow<List<PendingRequirement>> {
        return vaccinationDao.getAllVaccinations().map { entities ->
            val vaccinations = entities.map { it.toVaccination() }
            ReminderEngine.getUnsatisfiedRequirements(vaccinations)
        }
    }

    override fun getAllReminderHistory(): Flow<List<ReminderEntity>> {
        return reminderDao.getAllReminders()
    }

    override suspend fun markAsDone(requirement: PendingRequirement) = withContext(Dispatchers.IO) {
        // We only clear the reminders. 
        // Manual data entry is required for clinical records to avoid automatic entry.
        markPatientRemindersCompleted(requirement.patientId)
        triggerImmediateCheck()
    }

    override suspend fun reschedule(requirement: PendingRequirement, newDate: String, reason: String) = withContext(Dispatchers.IO) {
        val user = auth.currentUser?.email ?: "Unknown"
        val originalEntity = vaccinationDao.getVaccinationById(requirement.originalVisitId)
        
        if (originalEntity != null) {
            val updated = originalEntity.toVaccination().copy(
                nextDueDate = newDate,
                rescheduleReason = reason,
                performedBy = user
            )
            
            vaccinationDao.insertVaccination(updated.toEntity(isSynced = false))
            try {
                firestore.collection("vaccinations").document(updated.id).set(updated).await()
                vaccinationDao.insertVaccination(updated.toEntity(isSynced = true))
            } catch (e: Exception) { }

            markPatientRemindersCompleted(requirement.patientId)
            triggerImmediateCheck()
        }
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
        try {
            firestore.collection("vaccinations").document(newVaccination.id).set(newVaccination).await()
            vaccinationDao.insertVaccination(newVaccination.toEntity(isSynced = true))
        } catch (e: Exception) { }

        markPatientRemindersCompleted(requirement.patientId)
        triggerImmediateCheck()
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
