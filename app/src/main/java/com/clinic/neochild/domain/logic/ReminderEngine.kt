package com.clinic.neochild.domain.logic

import com.clinic.neochild.core.utils.PatientUtils
import com.clinic.neochild.domain.model.Vaccination
import com.clinic.neochild.domain.model.PendingRequirement
import java.util.*

/**
 * Pure Requirement Calculator.
 * Identifies potential gaps in a patient's vaccination schedule based purely
 * on medical records. It does NOT check manual overrides (dismissed, rescheduled).
 */
object ReminderEngine {

    /**
     * Analyzes vaccination history to find requirements that haven't been medically satisfied.
     * A requirement is satisfied if ANY visit occurring ON OR AFTER the
     * visit that created the requirement contains this vaccine in its "Gave" list.
     */
    fun getPotentialRequirements(allVaccinations: List<Vaccination>): List<PendingRequirement> {
        val requirements = mutableListOf<PendingRequirement>()
        val patientVisits = allVaccinations.groupBy { it.patientId }

        for ((patientId, visits) in patientVisits) {
            // Sort visits chronologically
            val sortedVisits = visits.sortedBy { PatientUtils.parseDate(it.dateGiven) ?: Date(0) }

            for (i in sortedVisits.indices) {
                val visit = sortedVisits[i]
                
                // If this visit doesn't specify any "next vaccines", it creates no requirements
                if (visit.nextDueDate.isBlank() || visit.nxtVaccineNames.isEmpty()) continue

                val dueDate = PatientUtils.parseDate(visit.nextDueDate) ?: continue

                for (dueVaccineName in visit.nxtVaccineNames) {
                    val cleanedDueName = PatientUtils.cleanVaccineName(dueVaccineName).lowercase().trim()
                    if (cleanedDueName.isBlank()) continue

                    // A requirement is satisfied if it was medically given in this or any subsequent visit.
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
