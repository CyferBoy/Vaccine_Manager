package com.clinic.neochild.domain.usecase.patient

import com.clinic.neochild.domain.model.Patient
import com.clinic.neochild.domain.model.Vaccination
import javax.inject.Inject

class SearchPatientsUseCase @Inject constructor() {
    operator fun invoke(
        query: String,
        patients: List<Patient>,
        vaccinations: List<Vaccination>
    ): List<Patient> {
        if (query.isBlank()) return patients

        val lowerQuery = query.lowercase().trim()
        
        // Find patient IDs from vaccinations that match vaccine name or receipt number
        val patientIdsFromVaccinations = vaccinations.filter { v ->
            v.vaccineNames.any { it.contains(lowerQuery, ignoreCase = true) } ||
            v.receiptNumber.contains(lowerQuery, ignoreCase = true)
        }.map { it.patientId }.toSet()

        return patients.filter { p ->
            p.name.contains(lowerQuery, ignoreCase = true) ||
            p.phone.contains(lowerQuery) ||
            p.parentName.contains(lowerQuery, ignoreCase = true) ||
            p.patientClinicId.contains(lowerQuery, ignoreCase = true) ||
            p.village.contains(lowerQuery, ignoreCase = true) ||
            p.address.contains(lowerQuery, ignoreCase = true) ||
            patientIdsFromVaccinations.contains(p.id)
        }
    }
}
