package com.clinic.neochild.utils

import com.clinic.neochild.data.model.Vaccination
import java.util.*

/**
 * Requirement-Based Reminder Engine
 * 
 * Instead of checking for "newer records", this engine tracks individual vaccine 
 * requirements (nxtVaccineNames) and checks if they have been "satisfied" by 
 * any subsequent administration (vaccineNames).
 */
object ReminderEngine {

    /**
     * Analyzes vaccination history to find requirements that haven't been met yet.
     */
    fun getUnsatisfiedRequirements(allVaccinations: List<Vaccination>): List<PendingRequirement> {
        val pending = mutableListOf<PendingRequirement>()
        
        // Group by patient to avoid cross-patient pollution and optimize sorting
        val patientVisits = allVaccinations.groupBy { it.patientId }

        for ((patientId, visits) in patientVisits) {
            // Sort visits chronologically
            val sortedVisits = visits.sortedBy { PatientUtils.parseDate(it.dateGiven) ?: Date(0) }

            for (i in sortedVisits.indices) {
                val visit = sortedVisits[i]
                
                // If this visit doesn't specify any "next vaccines", it creates no requirements
                if (visit.nextDueDate.isBlank() || visit.nxtVaccineNames.isEmpty()) continue

                val visitDate = PatientUtils.parseDate(visit.dateGiven) ?: continue
                val dueDate = PatientUtils.parseDate(visit.nextDueDate) ?: continue

                // Check each expected vaccine in this specific visit's "Next Due" list
                for (dueVaccineName in visit.nxtVaccineNames) {
                    val cleanedDueName = PatientUtils.cleanVaccineName(dueVaccineName).lowercase().trim()
                    if (cleanedDueName.isBlank()) continue

                    // A requirement is satisfied if ANY visit occurring ON OR AFTER the 
                    // visit that created the requirement contains this vaccine in its "Gave" list.
                    // Note: We check 'i' and onwards because the doctor might have given it the same day 
                    // (though rare, it handles data entry edge cases).
                    val isSatisfied = sortedVisits.drop(i).any { laterVisit ->
                        laterVisit.vaccineNames.any { givenName ->
                            PatientUtils.cleanVaccineName(givenName).lowercase().trim() == cleanedDueName
                        }
                    }

                    if (!isSatisfied) {
                        pending.add(
                            PendingRequirement(
                                patientId = patientId,
                                vaccineName = dueVaccineName,
                                dueDate = dueDate,
                                originalVisitId = visit.id
                            )
                        )
                    }
                }
            }
        }
        return pending
    }
}

/**
 * Represents a specific vaccine that is still missing from a patient's clinical path.
 */
data class PendingRequirement(
    val patientId: String,
    val vaccineName: String,
    val dueDate: Date,
    val originalVisitId: String
)
