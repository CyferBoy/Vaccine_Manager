package com.clinic.neochild.data.local.dao

import androidx.room.*
import com.clinic.neochild.data.local.entity.PatientEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientDao {
    @Query("SELECT * FROM patients WHERE isDeleted = 0")
    fun getAllPatients(): Flow<List<PatientEntity>>

    @Query("SELECT * FROM patients WHERE id = :id")
    suspend fun getPatientById(id: String): PatientEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPatient(patient: PatientEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPatients(patients: List<PatientEntity>)

    @Query("UPDATE patients SET isDeleted = 1, isSynced = 0 WHERE id = :id")
    suspend fun deletePatient(id: String)

    @Query("SELECT * FROM patients WHERE isSynced = 0")
    suspend fun getUnsyncedPatients(): List<PatientEntity>

    @Query("UPDATE patients SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("""
        SELECT * FROM patients 
        WHERE isDeleted = 0 
        AND (name LIKE :q OR phone LIKE :q OR parentName LIKE :q OR patientClinicId LIKE :q OR village LIKE :q OR address LIKE :q
             OR id IN (SELECT patientId FROM patient_visits WHERE isDeleted = 0 AND (vaccineNames LIKE :q OR receiptNumber LIKE :q)))
    """)
    fun searchPatients(q: String): Flow<List<PatientEntity>>

    @Query("SELECT COUNT(*) FROM patients WHERE isDeleted = 0")
    fun getPatientCount(): Flow<Int>
}
