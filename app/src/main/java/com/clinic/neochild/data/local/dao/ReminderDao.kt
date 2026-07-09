package com.clinic.neochild.data.local.dao

import androidx.room.*
import com.clinic.neochild.data.local.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY createdAt DESC")
    fun getAllReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE patientId = :patientId AND type = :type AND completed = 0")
    suspend fun getPendingPatientReminder(patientId: String, type: String): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE vaccineId = :vaccineId AND type = :type AND completed = 0")
    suspend fun getPendingVaccineReminder(vaccineId: String, type: String): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity): Long

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Query("UPDATE reminders SET completed = 1, status = 'DONE', updatedAt = :timestamp WHERE id = :id")
    suspend fun markCompleted(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE reminders SET completed = 1, status = 'DONE', updatedAt = :timestamp WHERE patientId = :patientId AND completed = 0")
    suspend fun markPatientRemindersCompleted(patientId: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM reminders WHERE completed = 1 AND updatedAt < :timestamp")
    suspend fun deleteOldCompletedReminders(timestamp: Long)
}
