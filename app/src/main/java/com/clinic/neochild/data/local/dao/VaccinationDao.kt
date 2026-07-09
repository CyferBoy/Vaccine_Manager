package com.clinic.neochild.data.local.dao

import androidx.room.*
import com.clinic.neochild.data.local.entity.VaccinationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VaccinationDao {
    @Query("SELECT * FROM vaccinations WHERE isDeleted = 0")
    fun getAllVaccinations(): Flow<List<VaccinationEntity>>

    @Query("SELECT * FROM vaccinations WHERE patientId = :patientId AND isDeleted = 0")
    fun getVaccinationsForPatient(patientId: String): Flow<List<VaccinationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaccination(vaccination: VaccinationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaccinations(vaccinations: List<VaccinationEntity>)

    @Query("UPDATE vaccinations SET isDeleted = 1, isSynced = 0 WHERE id = :id")
    suspend fun deleteVaccination(id: String)

    @Query("SELECT * FROM vaccinations WHERE isSynced = 0")
    suspend fun getUnsyncedVaccinations(): List<VaccinationEntity>

    @Query("UPDATE vaccinations SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("SELECT COUNT(*) FROM vaccinations WHERE nextDueDate = :date AND isDone = 0 AND isDeleted = 0")
    suspend fun getDueCount(date: String): Int
}
