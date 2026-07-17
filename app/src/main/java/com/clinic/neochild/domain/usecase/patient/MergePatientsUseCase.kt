package com.clinic.neochild.domain.usecase.patient

import com.clinic.neochild.domain.repository.PatientRepository
import com.clinic.neochild.domain.repository.VaccinationRepository
import com.clinic.neochild.domain.repository.SyncRepository
import com.clinic.neochild.domain.model.SyncOperation
import com.clinic.neochild.domain.model.SyncPriority
import com.clinic.neochild.data.local.AppDatabase
import com.clinic.neochild.domain.model.Vaccination
import com.clinic.neochild.domain.model.Patient
import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Business Logic to merge duplicate patient records.
 * Ensures data integrity across Patients and Vaccinations.
 */
class MergePatientsUseCase @Inject constructor(
    private val database: AppDatabase,
    private val patientRepository: PatientRepository,
    private val vaccinationRepository: VaccinationRepository,
    private val syncRepository: SyncRepository
) {
    suspend operator fun invoke(masterId: String, duplicateIds: List<String>) {
        if (masterId.isBlank() || duplicateIds.isEmpty()) return

        database.withTransaction {
            for (dupId in duplicateIds) {
                // 1. Move all vaccinations from duplicate to master
                val duplicateVaccinations = vaccinationRepository.getVaccinationsForPatient(dupId).first()
                for (vacc in duplicateVaccinations) {
                    val updatedVacc = vacc.copy(patientId = masterId)
                    vaccinationRepository.addVaccination(updatedVacc)
                    
                    // Queue Sync for updated vaccination
                    syncRepository.enqueue(
                        entityName = "VACCINATION",
                        entityId = updatedVacc.id,
                        operation = SyncOperation.UPDATE,
                        priority = SyncPriority.MEDIUM
                    )
                }

                // 2. Delete duplicate patient locally
                patientRepository.deletePatient(dupId)

                // 3. Queue Sync for patient deletion
                syncRepository.enqueue(
                    entityName = "PATIENT",
                    entityId = dupId,
                    operation = SyncOperation.DELETE,
                    priority = SyncPriority.MEDIUM
                )
            }
        }
    }
}
