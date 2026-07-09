package com.clinic.neochild.data.local.dao

import androidx.room.*
import com.clinic.neochild.data.local.entity.VaccineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VaccineDao {
    @Query("SELECT * FROM vaccines WHERE isDeleted = 0")
    fun getAllVaccines(): Flow<List<VaccineEntity>>

    @Query("SELECT * FROM vaccines WHERE id = :id AND isDeleted = 0")
    suspend fun getVaccineById(id: String): VaccineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaccine(vaccine: VaccineEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaccines(vaccines: List<VaccineEntity>)

    @Query("UPDATE vaccines SET isDeleted = 1 WHERE id = :id")
    suspend fun deleteVaccine(id: String)

    @Query("SELECT * FROM vaccines WHERE isSynced = 0")
    suspend fun getUnsyncedVaccines(): List<VaccineEntity>
}
