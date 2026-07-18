package com.clinic.neochild.data.local.dao

import androidx.room.*
import com.clinic.neochild.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DueReminderDao {
    // Due Reminders
    @Query("SELECT * FROM due_reminders WHERE isDeleted = 0 ORDER BY dueDate ASC")
    fun getAllDueReminders(): Flow<List<DueReminderEntity>>

    @Query("SELECT * FROM due_reminders WHERE patientId = :patientId AND originalVisitId = :visitId AND vaccineName = :vaccineName AND isDeleted = 0 LIMIT 1")
    suspend fun getDueReminder(patientId: String, visitId: String, vaccineName: String): DueReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDueReminder(reminder: DueReminderEntity): Long

    @Update
    suspend fun updateDueReminder(reminder: DueReminderEntity)

    @Query("DELETE FROM due_reminders WHERE patientId = :patientId AND originalVisitId = :visitId AND vaccineName = :vaccineName")
    suspend fun deleteDueReminder(patientId: String, visitId: String, vaccineName: String)

    @Query("UPDATE due_reminders SET isDeleted = 1 WHERE id = :id")
    suspend fun softDeleteDueReminder(id: Long)

    // Completed Reminders
    @Query("SELECT * FROM completed_reminders WHERE isDeleted = 0 ORDER BY completionDate DESC")
    fun getAllCompletedReminders(): Flow<List<CompletedReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletedReminder(reminder: CompletedReminderEntity): Long

    // Dismissed Reminders
    @Query("SELECT * FROM dismissed_reminders WHERE isDeleted = 0 ORDER BY dismissalDate DESC")
    fun getAllDismissedReminders(): Flow<List<DismissedReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDismissedReminder(reminder: DismissedReminderEntity): Long

    // External Reminders
    @Query("SELECT * FROM external_reminders WHERE isDeleted = 0 ORDER BY dueDate ASC")
    fun getAllExternalReminders(): Flow<List<ExternalReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExternalReminder(reminder: ExternalReminderEntity): Long

    // Common
    @Query("SELECT * FROM due_reminders WHERE patientId = :patientId AND isDeleted = 0")
    fun getDueRemindersForPatient(patientId: String): Flow<List<DueReminderEntity>>

    @Query("SELECT * FROM completed_reminders WHERE patientId = :patientId AND isDeleted = 0")
    fun getCompletedRemindersForPatient(patientId: String): Flow<List<CompletedReminderEntity>>

    @Query("SELECT * FROM dismissed_reminders WHERE patientId = :patientId AND isDeleted = 0")
    fun getDismissedRemindersForPatient(patientId: String): Flow<List<DismissedReminderEntity>>

    @Query("SELECT * FROM external_reminders WHERE patientId = :patientId AND isDeleted = 0")
    fun getExternalRemindersForPatient(patientId: String): Flow<List<ExternalReminderEntity>>

    @Transaction
    suspend fun moveDueToCompleted(reminder: DueReminderEntity, completedBy: String, notes: String? = null) {
        val completed = CompletedReminderEntity(
            patientId = reminder.patientId,
            originalVisitId = reminder.originalVisitId,
            vaccineName = reminder.vaccineName,
            dueDate = reminder.dueDate,
            completionDate = System.currentTimeMillis(),
            completedBy = completedBy,
            notes = notes ?: reminder.notes
        )
        insertCompletedReminder(completed)
        deleteDueReminder(reminder.patientId, reminder.originalVisitId, reminder.vaccineName)
    }

    @Transaction
    suspend fun moveDueToDismissed(reminder: DueReminderEntity, dismissedBy: String, reason: String? = null) {
        val dismissed = DismissedReminderEntity(
            patientId = reminder.patientId,
            originalVisitId = reminder.originalVisitId,
            vaccineName = reminder.vaccineName,
            dueDate = reminder.dueDate,
            dismissalDate = System.currentTimeMillis(),
            dismissedBy = dismissedBy,
            reason = reason
        )
        insertDismissedReminder(dismissed)
        deleteDueReminder(reminder.patientId, reminder.originalVisitId, reminder.vaccineName)
    }

    @Transaction
    suspend fun moveDueToExternal(reminder: DueReminderEntity, source: String, externalDate: String, recordedBy: String, notes: String? = null) {
        val external = ExternalReminderEntity(
            patientId = reminder.patientId,
            originalVisitId = reminder.originalVisitId,
            vaccineName = reminder.vaccineName,
            dueDate = reminder.dueDate,
            externalDate = externalDate,
            source = source,
            recordedBy = recordedBy,
            notes = notes ?: reminder.notes
        )
        insertExternalReminder(external)
        deleteDueReminder(reminder.patientId, reminder.originalVisitId, reminder.vaccineName)
    }
}
