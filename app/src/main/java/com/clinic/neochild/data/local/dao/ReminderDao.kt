package com.clinic.neochild.data.local.dao

import androidx.room.*
import com.clinic.neochild.data.local.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY updatedAt DESC")
    fun getAllReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE patientId = :patientId AND originalVisitId = :visitId AND vaccineName = :vaccineName LIMIT 1")
    suspend fun getReminderState(patientId: String, visitId: String, vaccineName: String): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE completed = 0")
    fun getActiveOverrides(): Flow<List<ReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(reminder: ReminderEntity): Long

    @Query("UPDATE reminders SET status = :status, completed = :isCompleted, updatedAt = :timestamp WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, isCompleted: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM reminders WHERE patientId = :patientId ORDER BY dueDate ASC")
    fun getFollowUpsForPatient(patientId: String): Flow<List<ReminderEntity>>

    @Query("DELETE FROM reminders WHERE patientId = :patientId AND originalVisitId = :visitId AND vaccineName = :vaccineName")
    suspend fun deleteReminder(patientId: String, visitId: String, vaccineName: String)

    @Query("UPDATE reminders SET completed = 1, status = 'COMPLETED', updatedAt = :timestamp WHERE patientId = :patientId")
    suspend fun markAllForPatientCompleted(patientId: String, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM reminders WHERE isSynced = 0")
    suspend fun getUnsyncedReminders(): List<ReminderEntity>

    @Query("UPDATE reminders SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: Long)
}
