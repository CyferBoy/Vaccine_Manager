package com.clinic.neochild.di

import com.clinic.neochild.data.repository.InventoryRepositoryImpl
import com.clinic.neochild.data.repository.PatientRepositoryImpl
import com.clinic.neochild.data.repository.ReminderRepositoryImpl
import com.clinic.neochild.data.repository.SyncManagerImpl
import com.clinic.neochild.data.repository.SyncRepositoryImpl
import com.clinic.neochild.data.repository.VaccinationRepositoryImpl
import com.clinic.neochild.data.repository.VaccineRepositoryImpl
import com.clinic.neochild.data.repository.WasteRepositoryImpl
import com.clinic.neochild.domain.repository.InventoryRepository
import com.clinic.neochild.domain.repository.PatientRepository
import com.clinic.neochild.domain.repository.ReminderRepository
import com.clinic.neochild.domain.repository.SyncManager
import com.clinic.neochild.domain.repository.SyncRepository
import com.clinic.neochild.domain.repository.VaccinationRepository
import com.clinic.neochild.domain.repository.VaccineRepository
import com.clinic.neochild.domain.repository.WasteRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPatientRepository(impl: PatientRepositoryImpl): PatientRepository

    @Binds
    @Singleton
    abstract fun bindVaccinationRepository(impl: VaccinationRepositoryImpl): VaccinationRepository

    @Binds
    @Singleton
    abstract fun bindReminderRepository(impl: ReminderRepositoryImpl): ReminderRepository

    @Binds
    @Singleton
    abstract fun bindVaccineRepository(impl: VaccineRepositoryImpl): VaccineRepository

    @Binds
    @Singleton
    abstract fun bindInventoryRepository(impl: InventoryRepositoryImpl): InventoryRepository

    @Binds
    @Singleton
    abstract fun bindSyncRepository(impl: SyncRepositoryImpl): SyncRepository

    @Binds
    @Singleton
    abstract fun bindSyncManager(impl: SyncManagerImpl): SyncManager

    @Binds
    @Singleton
    abstract fun bindWasteRepository(impl: WasteRepositoryImpl): WasteRepository
}
