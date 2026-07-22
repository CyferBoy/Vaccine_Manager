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

    @Query("SELECT * FROM due_reminders WHERE id = :id LIMIT 1")
    suspend fun getDueReminderById(id: Long): DueReminderEntity?

    @Query("SELECT * FROM due_reminders WHERE patientId = :pId AND originalVisitId = :vId AND vaccineName = :name LIMIT 1")
    suspend fun getDueReminderByStableId(pId: String, vId: String, name: String): DueReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDueReminder(reminder: DueReminderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDueReminders(reminders: List<DueReminderEntity>)

    @Update
    suspend fun updateDueReminder(reminder: DueReminderEntity)

    @Query("DELETE FROM due_reminders WHERE patientId = :patientId AND originalVisitId = :visitId AND vaccineName = :vaccineName")
    suspend fun deleteDueReminder(patientId: String, visitId: String, vaccineName: String)

    @Query("UPDATE due_reminders SET isDeleted = 1, isSynced = 0 WHERE id = :id")
    suspend fun softDeleteDueReminder(id: Long)

    // Completed Reminders
    @Query("SELECT * FROM completed_reminders WHERE isDeleted = 0 ORDER BY completionDate DESC")
    fun getAllCompletedReminders(): Flow<List<CompletedReminderEntity>>

    @Query("SELECT * FROM completed_reminders WHERE id = :id LIMIT 1")
    suspend fun getCompletedReminderById(id: Long): CompletedReminderEntity?

    @Query("SELECT * FROM completed_reminders WHERE patientId = :pId AND originalVisitId = :vId AND vaccineName = :name LIMIT 1")
    suspend fun getCompletedReminderByStableId(pId: String, vId: String, name: String): CompletedReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletedReminder(reminder: CompletedReminderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletedReminders(reminders: List<CompletedReminderEntity>)

    @Query("DELETE FROM completed_reminders WHERE patientId = :patientId AND originalVisitId = :visitId AND vaccineName = :vaccineName")
    suspend fun deleteCompletedReminder(patientId: String, visitId: String, vaccineName: String)

    // Dismissed Reminders
    @Query("SELECT * FROM dismissed_reminders WHERE isDeleted = 0 ORDER BY dismissalDate DESC")
    fun getAllDismissedReminders(): Flow<List<DismissedReminderEntity>>

    @Query("SELECT * FROM dismissed_reminders WHERE id = :id LIMIT 1")
    suspend fun getDismissedReminderById(id: Long): DismissedReminderEntity?

    @Query("SELECT * FROM dismissed_reminders WHERE patientId = :pId AND originalVisitId = :vId AND vaccineName = :name LIMIT 1")
    suspend fun getDismissedReminderByStableId(pId: String, vId: String, name: String): DismissedReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDismissedReminder(reminder: DismissedReminderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDismissedReminders(reminders: List<DismissedReminderEntity>)

    @Query("DELETE FROM dismissed_reminders WHERE patientId = :patientId AND originalVisitId = :visitId AND vaccineName = :vaccineName")
    suspend fun deleteDismissedReminder(patientId: String, visitId: String, vaccineName: String)

    // External Reminders
    @Query("SELECT * FROM external_reminders WHERE isDeleted = 0 ORDER BY dueDate ASC")
    fun getAllExternalReminders(): Flow<List<ExternalReminderEntity>>

    @Query("SELECT * FROM external_reminders WHERE id = :id LIMIT 1")
    suspend fun getExternalReminderById(id: Long): ExternalReminderEntity?

    @Query("SELECT * FROM external_reminders WHERE patientId = :pId AND originalVisitId = :vId AND vaccineName = :name LIMIT 1")
    suspend fun getExternalReminderByStableId(pId: String, vId: String, name: String): ExternalReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExternalReminder(reminder: ExternalReminderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExternalReminders(reminders: List<ExternalReminderEntity>)

    @Query("DELETE FROM external_reminders WHERE patientId = :patientId AND originalVisitId = :visitId AND vaccineName = :vaccineName")
    suspend fun deleteExternalReminder(patientId: String, visitId: String, vaccineName: String)

    @Query("DELETE FROM reminders WHERE patientId = :patientId AND originalVisitId = :visitId AND vaccineName = :vaccineName")
    suspend fun deleteOldReminder(patientId: String, visitId: String, vaccineName: String)

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
    suspend fun getLocalPriority(pId: String, vId: String, name: String): Int {
        if (getExternalReminderByStableId(pId, vId, name) != null) return 4
        if (getCompletedReminderByStableId(pId, vId, name) != null) return 3
        if (getDismissedReminderByStableId(pId, vId, name) != null) return 2
        if (getDueReminderByStableId(pId, vId, name) != null) return 1
        return 0
    }

    @Transaction
    suspend fun isLocalUnsynced(pId: String, vId: String, name: String): Boolean {
        if (getExternalReminderByStableId(pId, vId, name)?.isSynced == false) return true
        if (getCompletedReminderByStableId(pId, vId, name)?.isSynced == false) return true
        if (getDismissedReminderByStableId(pId, vId, name)?.isSynced == false) return true
        if (getDueReminderByStableId(pId, vId, name)?.isSynced == false) return true
        return false
    }

    @Transaction
    suspend fun clearAllStates(patientId: String, visitId: String, vaccineName: String) {
        deleteDueReminder(patientId, visitId, vaccineName)
        deleteCompletedReminder(patientId, visitId, vaccineName)
        deleteDismissedReminder(patientId, visitId, vaccineName)
        deleteExternalReminder(patientId, visitId, vaccineName)
        deleteOldReminder(patientId, visitId, vaccineName)
    }

    @Transaction
    suspend fun moveDueToCompleted(reminder: DueReminderEntity, completedBy: String, notes: String? = null): Long {
        val completed = CompletedReminderEntity(
            patientId = reminder.patientId,
            originalVisitId = reminder.originalVisitId,
            vaccineName = reminder.vaccineName,
            dueDate = reminder.dueDate,
            completionDate = System.currentTimeMillis(),
            completedBy = completedBy,
            notes = notes ?: reminder.notes
        )
        clearAllStates(reminder.patientId, reminder.originalVisitId, reminder.vaccineName)
        return insertCompletedReminder(completed)
    }

    @Transaction
    suspend fun moveDueToDismissed(reminder: DueReminderEntity, dismissedBy: String, reason: String? = null): Long {
        val dismissed = DismissedReminderEntity(
            patientId = reminder.patientId,
            originalVisitId = reminder.originalVisitId,
            vaccineName = reminder.vaccineName,
            dueDate = reminder.dueDate,
            dismissalDate = System.currentTimeMillis(),
            dismissedBy = dismissedBy,
            reason = reason
        )
        clearAllStates(reminder.patientId, reminder.originalVisitId, reminder.vaccineName)
        return insertDismissedReminder(dismissed)
    }

    @Transaction
    suspend fun moveDueToExternal(reminder: DueReminderEntity, source: String, externalDate: String, recordedBy: String, notes: String? = null): Long {
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
        clearAllStates(reminder.patientId, reminder.originalVisitId, reminder.vaccineName)
        return insertExternalReminder(external)
    }
}
