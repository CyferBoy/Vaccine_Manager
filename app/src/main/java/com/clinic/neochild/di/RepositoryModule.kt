package com.clinic.neochild.di

import com.clinic.neochild.data.datasource.patient.PatientLocalDataSource
import com.clinic.neochild.data.datasource.patient.PatientRemoteDataSource
import com.clinic.neochild.data.datasource.vaccination.VaccinationLocalDataSource
import com.clinic.neochild.data.datasource.vaccination.VaccinationRemoteDataSource
import com.clinic.neochild.data.repository.*
import com.clinic.neochild.domain.repository.*
import com.clinic.neochild.utils.AuditLogger
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun providePatientRepository(
        localDataSource: PatientLocalDataSource,
        remoteDataSource: PatientRemoteDataSource,
        vaccinationLocal: VaccinationLocalDataSource,
        vaccinationRemote: VaccinationRemoteDataSource,
        firestore: FirebaseFirestore,
        auditLogger: AuditLogger
    ): PatientRepository {
        return PatientRepositoryImpl(
            localDataSource, 
            remoteDataSource, 
            vaccinationLocal, 
            vaccinationRemote, 
            firestore,
            auditLogger
        )
    }

    @Provides
    @Singleton
    fun provideVaccinationRepository(
        localDataSource: VaccinationLocalDataSource,
        remoteDataSource: VaccinationRemoteDataSource,
        auditLogger: AuditLogger
    ): VaccinationRepository {
        return VaccinationRepositoryImpl(localDataSource, remoteDataSource, auditLogger)
    }

    @Provides
    @Singleton
    fun provideReminderRepository(
        repositoryImpl: ReminderRepositoryImpl
    ): ReminderRepository = repositoryImpl

    @Provides
    @Singleton
    fun provideVaccineRepository(
        repositoryImpl: VaccineRepositoryImpl
    ): VaccineRepository = repositoryImpl

    @Provides
    @Singleton
    fun provideInventoryRepository(
        repositoryImpl: InventoryRepositoryImpl
    ): InventoryRepository = repositoryImpl

    @Provides
    @Singleton
    fun provideSyncRepository(
        repositoryImpl: SyncRepositoryImpl
    ): SyncRepository = repositoryImpl
}
