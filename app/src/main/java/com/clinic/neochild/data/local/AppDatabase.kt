package com.clinic.neochild.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.clinic.neochild.data.local.dao.PatientDao
import com.clinic.neochild.data.local.dao.VaccinationDao
import com.clinic.neochild.data.local.entity.PatientEntity
import com.clinic.neochild.data.local.entity.VaccinationEntity
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(entities = [PatientEntity::class, VaccinationEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun patientDao(): PatientDao
    abstract fun vaccinationDao(): VaccinationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // Use a passphrase for SQLCipher. 
                // In a real app, this should be securely generated and stored in Android Keystore.
                val passphrase = "your_secure_passphrase_here".toByteArray()
                val factory = SupportOpenHelperFactory(passphrase)
                
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "neochild_db"
                )
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration() // Simple for development
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
