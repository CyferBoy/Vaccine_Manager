package com.clinic.neochild.domain.usecase.inventory

import com.clinic.neochild.data.local.dao.VaccineDao
import com.clinic.neochild.domain.model.InventoryTransactionType
import com.clinic.neochild.domain.repository.InventoryRepository
import com.clinic.neochild.domain.repository.VaccinationRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

data class BackfillResult(
    val vaccineName: String,
    val countFound: Int,
    val success: Boolean,
    val message: String
)

class BackfillInventoryUsageUseCase @Inject constructor(
    private val vaccinationRepository: VaccinationRepository,
    private val inventoryRepository: InventoryRepository,
    private val vaccineDao: VaccineDao
) {
    suspend fun execute(user: String): List<BackfillResult> {
        val results = mutableListOf<BackfillResult>()
        
        try {
            // 1. Get all vaccinations
            val allVaccinations = vaccinationRepository.allVaccinations.first()
            
            // 2. Count occurrences of each vaccine brand
            val usageCounts = allVaccinations
                .flatMap { it.vaccineNames }
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .groupingBy { it }
                .eachCount()

            if (usageCounts.isEmpty()) {
                return listOf(BackfillResult("General", 0, false, "No vaccination history found to backfill."))
            }

            // 3. Load all vaccines from inventory to match IDs
            val inventoryVaccines = vaccineDao.getAllVaccines().first()

            // 4. Perform deductions
            for ((vaccineName, count) in usageCounts) {
                val matchedVaccine = inventoryVaccines.find { 
                    it.brandName.contains(vaccineName, ignoreCase = true) 
                }

                if (matchedVaccine == null) {
                    results.add(BackfillResult(vaccineName, count, false, "No matching vaccine in inventory"))
                    continue
                }

                try {
                    inventoryRepository.deductStock(
                        vaccineId = matchedVaccine.id,
                        quantity = count,
                        user = user,
                        transactionType = InventoryTransactionType.ADJUSTMENT,
                        vaccinationId = null,
                        patientId = null
                    )
                    results.add(BackfillResult(matchedVaccine.brandName, count, true, "Deducted $count from stock"))
                } catch (e: Exception) {
                    results.add(BackfillResult(matchedVaccine.brandName, count, false, e.message ?: "Failed deduction"))
                }
            }
        } catch (e: Exception) {
            results.add(BackfillResult("System", 0, false, "Critical error during backfill: ${e.message}"))
        }

        return results
    }
}
