package com.clinic.neochild.data.local.database

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.clinic.neochild.data.local.dao.*
import com.clinic.neochild.data.local.entity.*
import com.clinic.neochild.core.utils.SecurityUtils
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        PatientEntity::class, 
        VaccinationEntity::class, 
        ReminderEntity::class, 
        DueReminderEntity::class,
        CompletedReminderEntity::class,
        DismissedReminderEntity::class,
        ExternalReminderEntity::class,
        VaccineEntity::class,
        ReminderAuditEntity::class,
        VaccineBatchEntity::class,
        InventoryTransactionEntity::class,
        SyncQueueEntity::class,
        WasteEntity::class,
        WidgetDueEntity::class,
        AuditLogEntity::class
    ], 
    version = 16,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun patientDao(): PatientDao
    abstract fun vaccinationDao(): VaccinationDao
    abstract fun reminderDao(): ReminderDao
    abstract fun dueReminderDao(): DueReminderDao
    abstract fun vaccineDao(): VaccineDao
    abstract fun reminderAuditDao(): ReminderAuditDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun wasteDao(): WasteDao
    abstract fun widgetDueDao(): WidgetDueDao
    abstract fun auditLogDao(): AuditLogDao

    companion object {
        private const val TAG = "AppDatabase"
        private const val DB_NAME = "neochild_db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `waste_records` (
                        `id` TEXT NOT NULL, `vaccineId` TEXT NOT NULL, `brandName` TEXT NOT NULL, 
                        `batchNumber` TEXT NOT NULL, `expiryDate` TEXT NOT NULL, `dateWasted` TEXT NOT NULL, 
                        `reason` TEXT NOT NULL, `quantity` INTEGER NOT NULL, `isSynced` INTEGER NOT NULL, 
                        `isDeleted` INTEGER NOT NULL, PRIMARY KEY(`id`)
                    )
                """)
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `widget_due_cache` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `patientName` TEXT NOT NULL, 
                        `vaccineName` TEXT NOT NULL, `dueDate` TEXT NOT NULL, `isOverdue` INTEGER NOT NULL
                    )
                """)
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add batchId to waste_records
                db.execSQL("ALTER TABLE `waste_records` ADD COLUMN `batchId` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add batchNumbers and expiryDates to vaccinations
                db.execSQL("ALTER TABLE `vaccinations` ADD COLUMN `batchNumbers` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `vaccinations` ADD COLUMN `expiryDates` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create new tables
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `due_reminders` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `patientId` TEXT NOT NULL, 
                        `originalVisitId` TEXT NOT NULL, `vaccineName` TEXT NOT NULL, `dueDate` TEXT NOT NULL, 
                        `reminderDate` TEXT NOT NULL, `status` TEXT NOT NULL, `priority` TEXT NOT NULL, 
                        `reminderEnabled` INTEGER NOT NULL, `category` TEXT NOT NULL, `notes` TEXT, 
                        `lastReminderTime` INTEGER NOT NULL, `notificationSent` INTEGER NOT NULL, 
                        `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `isSynced` INTEGER NOT NULL, 
                        `isDeleted` INTEGER NOT NULL
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_due_reminders_patientId_originalVisitId_vaccineName` ON `due_reminders` (`patientId`, `originalVisitId`, `vaccineName`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `completed_reminders` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `patientId` TEXT NOT NULL, 
                        `originalVisitId` TEXT NOT NULL, `vaccineName` TEXT NOT NULL, `dueDate` TEXT NOT NULL, 
                        `completionDate` INTEGER NOT NULL, `completedBy` TEXT NOT NULL, `notes` TEXT, 
                        `isSynced` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_completed_reminders_patientId_originalVisitId_vaccineName` ON `completed_reminders` (`patientId`, `originalVisitId`, `vaccineName`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `dismissed_reminders` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `patientId` TEXT NOT NULL, 
                        `originalVisitId` TEXT NOT NULL, `vaccineName` TEXT NOT NULL, `dueDate` TEXT NOT NULL, 
                        `dismissalDate` INTEGER NOT NULL, `dismissedBy` TEXT NOT NULL, `reason` TEXT, 
                        `isSynced` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_dismissed_reminders_patientId_originalVisitId_vaccineName` ON `dismissed_reminders` (`patientId`, `originalVisitId`, `vaccineName`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `external_reminders` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `patientId` TEXT NOT NULL, 
                        `originalVisitId` TEXT NOT NULL, `vaccineName` TEXT NOT NULL, `dueDate` TEXT NOT NULL, 
                        `externalDate` TEXT NOT NULL, `source` TEXT NOT NULL, `recordedBy` TEXT NOT NULL, 
                        `notes` TEXT, `isSynced` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_external_reminders_patientId_originalVisitId_vaccineName` ON `external_reminders` (`patientId`, `originalVisitId`, `vaccineName`)")

                // 2. Migrate data
                db.execSQL("""
                    INSERT INTO due_reminders (patientId, originalVisitId, vaccineName, dueDate, reminderDate, status, priority, reminderEnabled, category, notes, lastReminderTime, notificationSent, createdAt, updatedAt, isSynced, isDeleted)
                    SELECT patientId, originalVisitId, vaccineName, dueDate, dueDate, status, priority, reminderEnabled, category, notes, lastReminderTime, notificationSent, createdAt, updatedAt, isSynced, 0
                    FROM reminders WHERE completed = 0 AND status NOT IN ('COMPLETED', 'DISMISSED', 'EXTERNAL')
                """)

                db.execSQL("""
                    INSERT INTO completed_reminders (patientId, originalVisitId, vaccineName, dueDate, completionDate, completedBy, notes, isSynced, isDeleted)
                    SELECT patientId, originalVisitId, vaccineName, dueDate, updatedAt, 'MIGRATED', notes, isSynced, 0
                    FROM reminders WHERE status = 'COMPLETED' OR completed = 1
                """)

                db.execSQL("""
                    INSERT INTO dismissed_reminders (patientId, originalVisitId, vaccineName, dueDate, dismissalDate, dismissedBy, reason, isSynced, isDeleted)
                    SELECT patientId, originalVisitId, vaccineName, dueDate, updatedAt, 'MIGRATED', notes, isSynced, 0
                    FROM reminders WHERE status = 'DISMISSED'
                """)

                db.execSQL("""
                    INSERT INTO external_reminders (patientId, originalVisitId, vaccineName, dueDate, externalDate, source, recordedBy, notes, isSynced, isDeleted)
                    SELECT patientId, originalVisitId, vaccineName, dueDate, dueDate, IFNULL(vaccinationSource, 'UNKNOWN'), 'MIGRATED', notes, isSynced, 0
                    FROM reminders WHERE status = 'EXTERNAL'
                """)
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `audit_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `patientId` TEXT, 
                        `action` TEXT NOT NULL, `details` TEXT NOT NULL, `staffMember` TEXT NOT NULL, 
                        `timestamp` INTEGER NOT NULL, `device` TEXT, `isSynced` INTEGER NOT NULL
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_patientId` ON `audit_logs` (`patientId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_timestamp` ON `audit_logs` (`timestamp`)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val dbFile = context.getDatabasePath(DB_NAME)
                val isNewDatabase = !dbFile.exists()

                val passphrase = try {
                    SecurityUtils.getDatabasePassphrase(context, shouldGenerate = isNewDatabase)
                } catch (e: Exception) {
                    Log.e(TAG, "Passphrase unavailable. Preventing database initialization to protect data.", e)
                    // We throw here. The application should catch this at a higher level (e.g. in DatabaseModule or UI)
                    throw IllegalStateException("Security keys could not be loaded. Please ensure your device is unlocked.", e)
                }

                val factory = SupportOpenHelperFactory(passphrase)
                
                if (!isNewDatabase) {
                    Log.d(TAG, "Opening encrypted database...")
                    try {
                        net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(
                            dbFile.absolutePath, 
                            passphrase, 
                            null, 
                            net.zetetic.database.sqlcipher.SQLiteDatabase.OPEN_READONLY,
                            null
                        ).close()
                        Log.d(TAG, "Database opened successfully.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Database open failed. Key mismatch or corruption suspected.", e)
                        // CRITICAL: Never delete the database automatically.
                        throw IllegalStateException("Unable to access the encrypted database. Your security keys could not be loaded.", e)
                    }
                }

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                .openHelperFactory(factory)
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .addMigrations(MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
