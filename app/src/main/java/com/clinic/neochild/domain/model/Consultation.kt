package com.clinic.neochild.domain.model

data class Consultation(
    val id: String = "",
    val patientId: String = "",
    val date: String = "", // yyyy-MM-dd
    val amount: Double = 0.0,
    val notes: String = "",
    val nextFollowUpDate: String = ""
)
