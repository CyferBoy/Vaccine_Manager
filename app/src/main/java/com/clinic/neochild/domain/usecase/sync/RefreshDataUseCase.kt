package com.clinic.neochild.domain.usecase.sync

import com.clinic.neochild.domain.repository.PatientRepository
import com.clinic.neochild.domain.repository.VaccinationRepository
import com.clinic.neochild.domain.repository.WasteRepository
import com.clinic.neochild.domain.repository.InventoryRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

/**
 * Use case to trigger a full refresh of local data from remote sources.
 * Coordinates multiple repository refreshes.
 */
class RefreshDataUseCase @Inject constructor(
    private val patientRepository: PatientRepository,
    private val vaccinationRepository: VaccinationRepository,
    private val wasteRepository: WasteRepository,
    private val inventoryRepository: InventoryRepository
) {
    suspend operator fun invoke() = coroutineScope {
        val patientTask = async { patientRepository.refreshPatients() }
        val vaccinationTask = async { vaccinationRepository.refreshVaccinations() }
        val wasteTask = async { wasteRepository.refreshWaste() }
        val inventoryTask = async { inventoryRepository.refreshInventory() }
        
        patientTask.await()
        vaccinationTask.await()
        wasteTask.await()
        inventoryTask.await()
    }
}
