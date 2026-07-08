package com.clinic.neochild.data.model

data class Patient(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val alternatePhone: String = "",
    val dob: String = "",
    val gender: String = "",
    val address: String = "",
    val registrationDate: String = "",
) {
    // Secondary constructor or property to handle potential type mismatches if needed, 
    // but Firestore toObject is quite strict.
}
