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
     * Pure calculation: Analyzes vaccination history to find requirements.
     * A requirement is satisfied if ANY visit occurring ON OR AFTER the 
     * visit that created the requirement contains this vaccine in its "Gave" list.
     */
    fun getPotentialRequirements(allVaccinations: List<Vaccination>): List<PendingRequirement> {
        val requirements = mutableListOf<PendingRequirement>()
        val patientVisits = allVaccinations.groupBy { it.patientId }

        for ((patientId, visits) in patientVisits) {
            val sortedVisits = visits.sortedBy { PatientUtils.parseDate(it.dateGiven) ?: Date(0) }

            for (i in sortedVisits.indices) {
                val visit = sortedVisits[i]
                if (visit.nextDueDate.isBlank() || visit.nxtVaccineNames.isEmpty()) continue

                val dueDate = PatientUtils.parseDate(visit.nextDueDate) ?: continue

                for (dueVaccineName in visit.nxtVaccineNames) {
                    val cleanedDueName = PatientUtils.cleanVaccineName(dueVaccineName).lowercase().trim()
                    if (cleanedDueName.isBlank()) continue

                    val isSatisfiedByMedicalRecord = sortedVisits.drop(i).any { laterVisit ->
                        laterVisit.vaccineNames.any { givenName ->
                            PatientUtils.cleanVaccineName(givenName).lowercase().trim() == cleanedDueName
                        }
                    }

                    if (!isSatisfiedByMedicalRecord) {
                        requirements.add(
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
        return requirements
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
