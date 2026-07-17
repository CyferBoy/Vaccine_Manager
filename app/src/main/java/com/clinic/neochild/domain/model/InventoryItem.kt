package com.clinic.neochild.domain.model

data class InventoryItem(
    val id: String,
    val brandName: String,
    val stock: Int,
    val threshold: Int,
    val type: String,
    val company: String
)
