package com.clinic.neochild.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.clinic.neochild.data.local.entity.ReminderAuditEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderAuditDao {
    @Insert
    suspend fun insertAudit(audit: ReminderAuditEntity)

    @Query("SELECT * FROM reminder_audits WHERE patientId = :patientId ORDER BY timestamp DESC")
    fun getAuditsForPatient(patientId: String): Flow<List<ReminderAuditEntity>>

    @Query("SELECT * FROM reminder_audits WHERE isSynced = 0")
    suspend fun getUnsyncedAudits(): List<ReminderAuditEntity>

    @Query("UPDATE reminder_audits SET isSynced = 1 WHERE auditId = :id")
    suspend fun markSynced(id: Long)
}
