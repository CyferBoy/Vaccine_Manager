package com.clinic.neochild.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.clinic.neochild.data.local.dao.PatientDao
import com.clinic.neochild.data.local.dao.ReminderDao
import com.clinic.neochild.data.local.dao.VaccinationDao
import com.clinic.neochild.data.local.dao.VaccineDao
import com.clinic.neochild.data.local.entity.PatientEntity
import com.clinic.neochild.data.local.entity.ReminderEntity
import com.clinic.neochild.data.local.entity.VaccinationEntity
import com.clinic.neochild.data.local.entity.VaccineEntity
import com.clinic.neochild.utils.SecurityUtils
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        PatientEntity::class, 
        VaccinationEntity::class, 
        ReminderEntity::class, 
        VaccineEntity::class
    ], 
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun patientDao(): PatientDao
    abstract fun vaccinationDao(): VaccinationDao
    abstract fun reminderDao(): ReminderDao
    abstract fun vaccineDao(): VaccineDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // Use a secure passphrase from Android Keystore.
                val passphrase = SecurityUtils.getDatabasePassphrase(context)
                val factory = SupportOpenHelperFactory(passphrase)
                
                val dbFile = context.getDatabasePath("neochild_db")
                if (dbFile.exists()) {
                    try {
                        // Check if we can open the database. If not, delete it.
                        // This handles key mismatches during development.
                        net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(
                            dbFile.absolutePath, 
                            passphrase, 
                            null, 
                            net.zetetic.database.sqlcipher.SQLiteDatabase.OPEN_READONLY,
                            null
                        ).close()
                    } catch (e: Exception) {
                        context.deleteDatabase("neochild_db")
                    }
                }

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "neochild_db"
                )
                .openHelperFactory(factory)
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
