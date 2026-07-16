package com.clinic.neochild.data.model

/**
 * Uniquely identifies a specific vaccine requirement for a patient.
 * A single visit (originalVisitId) can have multiple vaccines (vaccineName).
 */
data class ReminderKey(
    val patientId: String,
    val originalVisitId: String,
    val vaccineName: String
)
