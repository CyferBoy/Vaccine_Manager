package com.clinic.neochild.core.utils

import com.clinic.neochild.data.local.dao.PatientDao
import java.util.Random
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PatientIdGenerator @Inject constructor(
    private val patientDao: PatientDao
) {
    /**
     * Generates a unique clinic ID.
     * Format: NEO-XXXX (where XXXX is a random 4-digit number)
     */
    suspend fun generateUniqueClinicId(): String {
        var clinicId: String
        var isUnique = false
        var attempts = 0
        
        do {
            val randomNum = Random().nextInt(9000) + 1000 // 1000 to 9999
            clinicId = "NEO-$randomNum"
            
            // Verify uniqueness
            val existing = patientDao.getPatientByClinicId(clinicId)
            if (existing == null) {
                isUnique = true
            }
            attempts++
        } while (!isUnique && attempts < 100)

        if (!isUnique) {
            // Fallback to a longer ID if collisions are high
            clinicId = "NEO-${System.currentTimeMillis() % 1000000}"
        }
        
        return clinicId
    }

    /**
     * Verifies if a given ID is unique.
     */
    suspend fun isIdUnique(clinicId: String, currentPatientId: String? = null): Boolean {
        if (clinicId.isBlank()) return true
        val existing = patientDao.getPatientByClinicId(clinicId)
        return existing == null || existing.id == currentPatientId
    }
}
