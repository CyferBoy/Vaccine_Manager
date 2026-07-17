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

    @Query("SELECT * FROM vaccinations WHERE id = :id AND isDeleted = 0")
    suspend fun getVaccinationById(id: String): VaccinationEntity?

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

    @Query("SELECT COUNT(*) FROM vaccinations WHERE dateGiven = :date AND isDone = 1 AND isDeleted = 0")
    fun getCountByDate(date: String): Flow<Int>

    @Query("SELECT SUM(totalPaid) FROM vaccinations WHERE dateGiven = :date AND isDone = 1 AND isDeleted = 0")
    fun getRevenueByDate(date: String): Flow<Double?>

    @Query("SELECT SUM(cashAmount) FROM vaccinations WHERE dateGiven = :date AND isDone = 1 AND isDeleted = 0")
    fun getCashByDate(date: String): Flow<Double?>

    @Query("SELECT SUM(onlineAmount) FROM vaccinations WHERE dateGiven = :date AND isDone = 1 AND isDeleted = 0")
    fun getOnlineByDate(date: String): Flow<Double?>

    @Query("SELECT COUNT(*) FROM vaccinations WHERE dateGiven LIKE :monthPattern AND isDone = 1 AND isDeleted = 0")
    fun getMonthlyCount(monthPattern: String): Flow<Int>

    @Query("SELECT SUM(totalPaid) FROM vaccinations WHERE dateGiven LIKE :monthPattern AND isDone = 1 AND isDeleted = 0")
    fun getMonthlyRevenue(monthPattern: String): Flow<Double?>

    @Query("SELECT vaccineNames FROM vaccinations WHERE dateGiven LIKE :monthPattern AND isDone = 1 AND isDeleted = 0")
    fun getVaccineNamesForMonth(monthPattern: String): Flow<List<String>>
}
