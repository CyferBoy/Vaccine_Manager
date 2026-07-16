package com.clinic.neochild.data.model

data class Patient(
    val id: String = "",
    val patientClinicId: String = "", // e.g., NEO-001
    val name: String = "",
    val parentName: String = "",
    val phone: String = "",
    val alternatePhone: String = "",
    val dob: String = "",
    val gender: String = "",
    val village: String = "",
    val address: String = "",
    val registrationDate: String = "",
)
