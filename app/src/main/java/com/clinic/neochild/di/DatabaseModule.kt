package com.clinic.neochild.di

import android.content.Context
import com.clinic.neochild.data.local.AppDatabase
import com.clinic.neochild.data.local.dao.PatientDao
import com.clinic.neochild.data.local.dao.ReminderDao
import com.clinic.neochild.data.local.dao.ReminderAuditDao
import com.clinic.neochild.data.local.dao.VaccinationDao
import com.clinic.neochild.data.local.dao.VaccineDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    fun providePatientDao(database: AppDatabase): PatientDao {
        return database.patientDao()
    }

    @Provides
    fun provideVaccinationDao(database: AppDatabase): VaccinationDao {
        return database.vaccinationDao()
    }

    @Provides
    fun provideReminderDao(database: AppDatabase): ReminderDao {
        return database.reminderDao()
    }

    @Provides
    fun provideReminderAuditDao(database: AppDatabase): ReminderAuditDao {
        return database.reminderAuditDao()
    }

    @Provides
    fun provideVaccineDao(database: AppDatabase): VaccineDao {
        return database.vaccineDao()
    }
}
