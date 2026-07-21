package com.clinic.neochild.domain.manager

import androidx.room.withTransaction
import com.clinic.neochild.data.local.database.AppDatabase
import com.clinic.neochild.data.local.dao.VaccineDao
import com.clinic.neochild.core.logger.AuditLogger
import com.clinic.neochild.domain.model.*
import com.clinic.neochild.domain.repository.*
import com.clinic.neochild.core.utils.PatientUtils
import com.clinic.neochild.domain.logic.ReminderEngine
import kotlinx.coroutines.flow.first
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single point of truth for the Vaccination business process.
 * Coordinates between Vaccination, Inventory, Reminders, and Sync repositories.
 * 
 * Satisfies Production Standard: Every business rule must exist in exactly one place.
 */
@Singleton
class VaccinationManager @Inject constructor(
    private val database: AppDatabase,
    private val vaccinationRepository: VaccinationRepository,
    private val inventoryRepository: InventoryRepository,
    private val reminderRepository: ReminderRepository,
    private val vaccineDao: VaccineDao,
    private val auditLogger: AuditLogger
) {
    /**
     * Completes a vaccination event with explicit parameters.
     * Ensures atomic update across all affected domains.
     */
    suspend fun completeVaccination(
        vaccination: Vaccination,
        user: String,
        isNew: Boolean = true,
        selectedVaccineIds: List<String> = emptyList(),
        requirement: PendingRequirement? = null,
        selectedBatchIds: List<String> = emptyList()
    ) {
        database.withTransaction {
            // 1. Add/Update Vaccination Record
            vaccinationRepository.addVaccination(vaccination)

            // 2. Inventory Management (Only for new vaccinations to prevent duplicate deduction)
            if (isNew) {
                if (selectedBatchIds.isNotEmpty()) {
                    // Deduct from specific batches if provided
                    selectedBatchIds.forEach { batchId ->
                        inventoryRepository.deductStockFromBatch(
                            batchId = batchId,
                            quantity = 1,
                            user = user,
                            transactionType = InventoryTransactionType.VACCINATION
                        )
                    }
                } else {
                    // Fallback to FEFO by vaccineId
                    selectedVaccineIds.forEach { vaccineId ->
                        inventoryRepository.deductStock(
                            vaccineId = vaccineId,
                            quantity = 1,
                            user = user,
                            transactionType = InventoryTransactionType.VACCINATION,
                            vaccinationId = vaccination.id,
                            patientId = vaccination.patientId
                        )
                    }
                }
            }

            // 3. Reminder Engine Satisfaction
            requirement?.let {
                reminderRepository.markRequirementSatisfied(it, user)
            }

            // 4. Audit Logging
            auditLogger.logAction(
                action = if (isNew) "Vaccination Added" else "Vaccination Updated",
                patientId = vaccination.patientId,
                details = "${vaccination.vaccineNames.joinToString(", ")} recorded by $user"
            )
        }
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
        val matchingVaccineId = vaccines.find { 
            it.brandName.contains(requirement.vaccineName, ignoreCase = true) 
        }?.id

        val selectedIds = matchingVaccineId?.let { listOf(it) } ?: emptyList()

        completeVaccination(
            vaccination = vaccination,
            user = user,
            isNew = true,
            selectedVaccineIds = selectedIds,
            requirement = requirement
        )
    }

    /**
     * Marks an existing vaccination record as completed and satisfies related reminders.
     */
    suspend fun satisfyExistingVaccination(
        vaccinationId: String,
        user: String
    ) {
        database.withTransaction {
            vaccinationRepository.markAsDone(vaccinationId)

            val allVaccs = vaccinationRepository.allVaccinations.first()
            val target = allVaccs.find { it.id == vaccinationId } ?: return@withTransaction

            val allRequirements = ReminderEngine.getPotentialRequirements(allVaccs)
            val matching = allRequirements.filter { req ->
                req.patientId == target.patientId && target.nxtVaccineNames.contains(req.vaccineName)
            }
            
            for (req in matching) {
                reminderRepository.markRequirementSatisfied(req, user)
            }
        }
    }
}
