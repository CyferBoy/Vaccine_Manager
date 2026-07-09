package com.clinic.neochild.data.model

data class Vaccination(
    val id: String = "",
    val patientId: String = "",
    val vaccineNames: List<String> = emptyList(),
    val nxtVaccineNames: List<String> = emptyList(),
    val dateGiven: String = "",
    val nextDueDate: String = "",
    val cost: Double = 0.0,
    val cashAmount: Double = 0.0,
    val onlineAmount: Double = 0.0,
    val totalPaid: Double = 0.0,
    val withFees: Boolean = false,
    val doctorsAcc: Boolean = false,
    val isDone: Boolean = false,
    val source: String = VaccinationSource.CLINIC.name,
    val notes: String = "",
    val rescheduleReason: String = "",
    val performedBy: String = "",
    val batchNumbers: List<String> = emptyList(),
    val expiryDates: List<String> = emptyList(),
    val batchNumber: String = "",
    val expiryDate: String = "",
    @Deprecated("Use vaccineNames instead")
    val vaccineName: String = "",
    @Deprecated("Use nxtVaccineNames instead")
    val nxtVaccineName: String = ""
)
