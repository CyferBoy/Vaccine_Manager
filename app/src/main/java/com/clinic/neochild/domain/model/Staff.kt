package com.clinic.neochild.domain.model

data class Staff(
    val id: String = "",
    val email: String = "",
    val name: String = "",
    val role: String = "Staff",
    val createdAt: Long = System.currentTimeMillis()
)
