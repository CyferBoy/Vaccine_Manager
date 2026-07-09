package com.clinic.neochild.di

import com.clinic.neochild.data.datasource.patient.PatientLocalDataSource
import com.clinic.neochild.data.datasource.patient.PatientLocalDataSourceImpl
import com.clinic.neochild.data.datasource.patient.PatientRemoteDataSource
import com.clinic.neochild.data.datasource.patient.PatientRemoteDataSourceImpl
import com.clinic.neochild.data.datasource.vaccination.VaccinationLocalDataSource
import com.clinic.neochild.data.datasource.vaccination.VaccinationLocalDataSourceImpl
import com.clinic.neochild.data.datasource.vaccination.VaccinationRemoteDataSource
import com.clinic.neochild.data.datasource.vaccination.VaccinationRemoteDataSourceImpl
import com.clinic.neochild.data.local.dao.PatientDao
import com.clinic.neochild.data.local.dao.VaccinationDao
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataSourceModule {

    @Provides
    @Singleton
    fun providePatientLocalDataSource(patientDao: PatientDao): PatientLocalDataSource {
        return PatientLocalDataSourceImpl(patientDao)
    }

    @Provides
    @Singleton
    fun providePatientRemoteDataSource(firestore: FirebaseFirestore): PatientRemoteDataSource {
        return PatientRemoteDataSourceImpl(firestore)
    }

    @Provides
    @Singleton
    fun provideVaccinationLocalDataSource(vaccinationDao: VaccinationDao): VaccinationLocalDataSource {
        return VaccinationLocalDataSourceImpl(vaccinationDao)
    }

    @Provides
    @Singleton
    fun provideVaccinationRemoteDataSource(firestore: FirebaseFirestore): VaccinationRemoteDataSource {
        return VaccinationRemoteDataSourceImpl(firestore)
    }
}
