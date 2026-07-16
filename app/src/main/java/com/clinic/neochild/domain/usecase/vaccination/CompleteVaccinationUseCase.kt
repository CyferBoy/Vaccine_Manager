package com.clinic.neochild.domain.usecase.vaccination

import androidx.room.withTransaction
import com.clinic.neochild.data.model.Vaccination
import com.clinic.neochild.domain.repository.*
import javax.inject.Inject

class CompleteVaccinationUseCase @Inject constructor(
    private val database: com.clinic.neochild.data.local.AppDatabase,
    private val vaccinationRepository: VaccinationRepository,
    private val inventoryRepository: InventoryRepository,
    private val syncRepository: SyncRepository
) {
    suspend operator fun invoke(
        vaccination: Vaccination, 
        isNew: Boolean, 
        selectedVaccineIds: List<String>,
        user: String
    ) {
        database.withTransaction {
            // 1. Save Vaccination Record
            vaccinationRepository.addVaccination(vaccination)

            if (isNew) {
                // 2. Deduct Inventory (FEFO)
                selectedVaccineIds.forEach { vaccineId ->
                    inventoryRepository.deductStock(
                        vaccineId = vaccineId,
                        quantity = 1,
                        user = user,
                        transactionType = com.clinic.neochild.data.model.InventoryTransactionType.VACCINATION,
                        vaccinationId = vaccination.id,
                        patientId = vaccination.patientId
                    )
                }

                // 3. Queue Sync for Vaccination
                syncRepository.enqueue(
                    entityName = "VACCINATION",
                    entityId = vaccination.id,
                    operation = com.clinic.neochild.data.model.SyncOperation.CREATE
                )
            }
        }
    }
}
