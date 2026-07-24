package com.clinic.neochild.di

import com.clinic.neochild.domain.repository.InventoryRepository
import com.clinic.neochild.domain.repository.PatientRepository
import com.clinic.neochild.domain.repository.ReminderRepository
import com.clinic.neochild.domain.repository.VaccinationRepository
import com.clinic.neochild.domain.repository.WasteRepository
import com.clinic.neochild.domain.usecase.patient.DeletePatientUseCase
import com.clinic.neochild.domain.usecase.patient.GetPatientsUseCase
import com.clinic.neochild.domain.usecase.patient.SavePatientUseCase
import com.clinic.neochild.domain.usecase.sync.RefreshDataUseCase
import com.clinic.neochild.domain.usecase.vaccination.DeleteVaccinationUseCase
import com.clinic.neochild.domain.usecase.vaccination.GetVaccinationsUseCase
import com.clinic.neochild.domain.usecase.vaccination.SaveVaccinationUseCase
import com.clinic.neochild.domain.usecase.inventory.BackfillInventoryUsageUseCase
import com.clinic.neochild.data.local.dao.VaccineDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
object UseCaseModule {

    @Provides
    @ViewModelScoped
    fun provideGetPatientsUseCase(repository: PatientRepository) = GetPatientsUseCase(repository)

    @Provides
    @ViewModelScoped
    fun provideSavePatientUseCase(repository: PatientRepository) = SavePatientUseCase(repository)

    @Provides
    @ViewModelScoped
    fun provideDeletePatientUseCase(repository: PatientRepository) = DeletePatientUseCase(repository)

    @Provides
    @ViewModelScoped
    fun provideGetVaccinationsUseCase(repository: VaccinationRepository) = GetVaccinationsUseCase(repository)

    @Provides
    @ViewModelScoped
    fun provideSaveVaccinationUseCase(repository: VaccinationRepository) = SaveVaccinationUseCase(repository)

    @Provides
    @ViewModelScoped
    fun provideDeleteVaccinationUseCase(repository: VaccinationRepository) = DeleteVaccinationUseCase(repository)

    @Provides
    @ViewModelScoped
    fun provideRefreshDataUseCase(
        patientRepository: PatientRepository,
        vaccinationRepository: VaccinationRepository,
        wasteRepository: WasteRepository,
        inventoryRepository: InventoryRepository,
        reminderRepository: ReminderRepository
    ) = RefreshDataUseCase(patientRepository, vaccinationRepository, wasteRepository, inventoryRepository, reminderRepository)

    @Provides
    @ViewModelScoped
    fun provideBackfillInventoryUsageUseCase(
        vaccinationRepository: VaccinationRepository,
        inventoryRepository: InventoryRepository,
        vaccineDao: VaccineDao
    ) = BackfillInventoryUsageUseCase(vaccinationRepository, inventoryRepository, vaccineDao)
}
