package com.clinic.neochild.domain.model

import com.clinic.neochild.data.local.entity.VaccineBatchEntity

data class InventoryItem(
    val id: String,
    val brandName: String,
    val stock: Int,
    val threshold: Int,
    val type: String,
    val company: String,
    val batches: List<VaccineBatchEntity> = emptyList()
)
