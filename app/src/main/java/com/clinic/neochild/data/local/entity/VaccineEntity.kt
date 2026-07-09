package com.clinic.neochild.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.clinic.neochild.data.model.Vaccine

@Entity(tableName = "vaccines")
data class VaccineEntity(
    @PrimaryKey val id: String,
    val type: String,
    val brandName: String,
    val companyName: String,
    val stock: Int,
    val batchNumber: String,
    val expiryDate: String,
    val mrp: Double,
    val netRate: Double,
    val isSynced: Boolean = true,
    val isDeleted: Boolean = false
)

fun VaccineEntity.toVaccine() = Vaccine(
    id = id,
    type = type,
    brandName = brandName,
    companyName = companyName,
    stock = stock,
    batchNumber = batchNumber,
    expiryDate = expiryDate,
    mrp = mrp,
    netRate = netRate
)

fun Vaccine.toEntity(isSynced: Boolean = true) = VaccineEntity(
    id = id,
    type = type,
    brandName = brandName,
    companyName = companyName,
    stock = stock,
    batchNumber = batchNumber,
    expiryDate = expiryDate,
    mrp = mrp,
    netRate = netRate,
    isSynced = isSynced
)
