package com.clinic.neochild.data.local.dao

import androidx.room.*
import com.clinic.neochild.data.local.entity.StaffEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StaffDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(staff: StaffEntity)

    @Query("SELECT * FROM staff_profiles WHERE isActive = 1")
    fun getActiveStaff(): Flow<List<StaffEntity>>

    @Query("SELECT * FROM staff_profiles WHERE email = :email LIMIT 1")
    suspend fun getStaffByEmail(email: String): StaffEntity?

    @Query("DELETE FROM staff_profiles WHERE id = :id")
    suspend fun deleteProfile(id: String)
}
