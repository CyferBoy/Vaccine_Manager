package com.clinic.neochild.domain.manager

import androidx.room.withTransaction
import com.clinic.neochild.data.local.database.AppDatabase
import com.clinic.neochild.data.local.dao.VaccineDao
import com.clinic.neochild.core.logger.AuditLogger
import com.clinic.neochild.domain.model.*
import com.clinic.neochild.domain.repository.*
import com.clinic.neochild.core.utils.PatientUtils
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
            // Enrich vaccination with batch info if missing but batches were selected
            val finalVaccination = if (selectedBatchIds.isNotEmpty() && vaccination.expiryDates.isEmpty()) {
                val batchDetails = selectedBatchIds.mapNotNull { id -> vaccineDao.getBatchById(id) }
                vaccination.copy(
                    batchNumbers = batchDetails.map { it.batchNumber },
                    expiryDates = batchDetails.map { it.expiryDate }
                )
            } else {
                vaccination
            }
            
            vaccinationRepository.addVaccination(finalVaccination)

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
            if (requirement != null) {
                reminderRepository.markRequirementSatisfied(requirement, user)
            } else if (isNew) {
                // Automatic satisfaction for manually added vaccines
                satisfyRelatedReminders(vaccination, user)
            }

            // 4. Audit Logging
            auditLogger.log(
                module = "VACCINATION",
                entityType = "VACCINATION",
                entityId = vaccination.id,
                action = "VACCINATION",
                patientId = vaccination.patientId,
                remarks = "${vaccination.vaccineNames.joinToString(", ")} recorded by $user"
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
        database.withTransaction {
            vaccinationRepository.markAsDone(vaccinationId)

            val target = vaccinationRepository.allVaccinations.first().find { it.id == vaccinationId } ?: return@withTransaction
            satisfyRelatedReminders(target, user)
        }
    }

    /**
     * Finds and satisfies any existing reminders that match the vaccines given in this visit.
     */
    private suspend fun satisfyRelatedReminders(vaccination: Vaccination, user: String) {
        val existingReminders = reminderRepository.getPatientFollowUps(vaccination.patientId).first()
        val activeReminders = existingReminders.filter { 
            (it.status == "ACTIVE" || it.status == "RESCHEDULED") && !it.isDeleted 
        }

        val givenCleaned = vaccination.vaccineNames.map { 
            PatientUtils.cleanVaccineName(it).lowercase().trim() 
        }

        for (reminder in activeReminders) {
            val reminderCleaned = PatientUtils.cleanVaccineName(reminder.vaccineName).lowercase().trim()
            if (givenCleaned.contains(reminderCleaned)) {
                reminderRepository.markRequirementSatisfied(
                    PendingRequirement(
                        patientId = reminder.patientId,
                        vaccineName = reminder.vaccineName,
                        dueDate = PatientUtils.parseDate(reminder.dueDate) ?: Date(),
                        originalVisitId = reminder.originalVisitId
                    ),
                    user
                )
            }
        }
    }
}
