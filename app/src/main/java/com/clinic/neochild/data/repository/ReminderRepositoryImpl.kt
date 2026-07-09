package com.clinic.neochild.data.repository

import com.clinic.neochild.data.local.dao.ReminderDao
import com.clinic.neochild.data.local.entity.ReminderEntity
import com.clinic.neochild.data.model.ReminderType
import com.clinic.neochild.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderRepositoryImpl @Inject constructor(
    private val reminderDao: ReminderDao
) : ReminderRepository {

    override fun getAllReminders(): Flow<List<ReminderEntity>> {
        return reminderDao.getAllReminders()
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

    override suspend fun updateReminder(reminder: ReminderEntity) {
        reminderDao.updateReminder(reminder)
    }

    override suspend fun markCompleted(id: Long) {
        reminderDao.markCompleted(id)
    }

    override suspend fun markPatientRemindersCompleted(patientId: String) {
        reminderDao.markPatientRemindersCompleted(patientId)
    }

    override suspend fun deleteOldReminders(days: Int) {
        val timestamp = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
        reminderDao.deleteOldCompletedReminders(timestamp)
    }
}
