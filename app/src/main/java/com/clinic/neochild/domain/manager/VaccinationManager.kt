package com.clinic.neochild.domain.manager

import com.clinic.neochild.domain.model.*
import com.clinic.neochild.domain.repository.*
import com.clinic.neochild.domain.service.ClinicalVaccinationService
import com.clinic.neochild.domain.service.InventoryProcessingService
import com.clinic.neochild.data.local.dao.VaccineDao
import com.clinic.neochild.core.utils.PatientUtils
import kotlinx.coroutines.flow.first
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single point of truth for the Vaccination business process.
 * Orchestrates between Clinical and Inventory domains.
 */
@Singleton
class VaccinationManager @Inject constructor(
    private val clinicalService: ClinicalVaccinationService,
    private val inventoryService: InventoryProcessingService,
    private val vaccinationRepository: VaccinationRepository,
    private val vaccineDao: VaccineDao
) {
    /**
     * Completes a vaccination event with explicit parameters.
     * Decouples Clinical save from Inventory deduction.
     */
    suspend fun completeVaccination(
        vaccination: Vaccination,
        user: String,
        isNew: Boolean = true,
        selectedVaccineIds: List<String> = emptyList(),
        requirement: PendingRequirement? = null,
        selectedBatchIds: List<String> = emptyList()
    ): String? {
        // 1. Clinical Domain ALWAYS Saves First
        clinicalService.recordVaccination(
            vaccination = vaccination,
            user = user,
            isNew = isNew,
            requirement = requirement
        )

        // 2. Attempt Inventory Domain deduction (Secondary)
        if (isNew) {
            return inventoryService.processVaccinationInventory(
                vaccinationId = vaccination.id,
                patientId = vaccination.patientId,
                vaccineIds = selectedVaccineIds,
                batchIds = selectedBatchIds,
                user = user
            )
        }
        
        return null
    }

    /**
     * Specialized flow to complete a vaccination directly from a pending requirement.
     * Automatically handles inventory mapping and record creation.
     */
    suspend fun completeFromRequirement(
        requirement: PendingRequirement,
        user: String,
        notes: String = ""
    ) {
        val vaccination = Vaccination(
            id = UUID.randomUUID().toString(),
            patientId = requirement.patientId,
            vaccineNames = listOf(requirement.vaccineName),
            dateGiven = PatientUtils.formatDate(Date()),
            isDone = true,
            source = VaccinationSource.CLINIC.name,
            performedBy = user,
            notes = notes
        )

        // Automated Inventory Mapping
        val vaccines = vaccineDao.getAllVaccines().first()
        val matchingVaccine = vaccines.find { 
            it.brandName.contains(requirement.vaccineName, ignoreCase = true) 
        }
        
        val matchingVaccineId = matchingVaccine?.id
        val selectedIds = matchingVaccineId?.let { listOf(it) } ?: emptyList()

        // Attempt to find FEFO batch to record expiry date even for automated completions
        var enrichedVaccination = vaccination
        val selectedBatchIds = mutableListOf<String>()
        
        if (matchingVaccineId != null) {
            val activeBatches = vaccineDao.getActiveBatchesByExpiry(matchingVaccineId)
            val firstBatch = activeBatches.firstOrNull { it.remainingQuantity > 0 && !com.clinic.neochild.core.utils.InventoryUtils.isExpired(it.expiryDate) }
            if (firstBatch != null) {
                enrichedVaccination = vaccination.copy(
                    batchNumbers = listOf(firstBatch.batchNumber),
                    expiryDates = listOf(firstBatch.expiryDate)
                )
                selectedBatchIds.add(firstBatch.batchId)
            }
        }

        completeVaccination(
            vaccination = enrichedVaccination,
            user = user,
            isNew = true,
            selectedVaccineIds = selectedIds,
            requirement = requirement,
            selectedBatchIds = selectedBatchIds
        )
    }

    /**
     * Marks an existing vaccination record as completed and satisfies related reminders.
     */
    suspend fun satisfyExistingVaccination(
        vaccinationId: String,
        user: String
    ) {
        // Delegating clinical part to service would require more refactoring of repository.
        // For now, satisfy existing is purely clinical.
        vaccinationRepository.markAsDone(vaccinationId)
        val target = vaccinationRepository.allVaccinations.first().find { it.id == vaccinationId } ?: return
        
        // This logic is duplicated in clinicalService.recordVaccination for internal use, 
        // but satisfyExisting calls it on an OLD record.
        // I'll keep it here but using the repository markAsDone which handles internal satisfaction if needed.
    }
}
