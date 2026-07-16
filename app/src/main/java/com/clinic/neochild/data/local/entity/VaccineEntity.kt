package com.clinic.neochild.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.clinic.neochild.data.model.BatchStatus
import com.clinic.neochild.data.model.Vaccine

@Entity(tableName = "vaccines")
data class VaccineEntity(
    @PrimaryKey val id: String,
    val type: String,
    val brandName: String,
    val companyName: String,
    val lowStockThreshold: Int = 5,
    val lastUpdated: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "vaccine_batches",
    foreignKeys = [
        ForeignKey(
            entity = VaccineEntity::class,
            parentColumns = ["id"],
            childColumns = ["vaccineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("vaccineId")]
)
data class VaccineBatchEntity(
    @PrimaryKey val batchId: String,
    val vaccineId: String,
    val batchNumber: String,
    val manufacturer: String,
    val purchaseDate: String,
    val expiryDate: String,
    val purchaseQuantity: Int,
    val remainingQuantity: Int,
    val supplier: String,
    val purchaseCost: Double,
    val sellingPrice: Double,
    val status: String = BatchStatus.ACTIVE.name,
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "inventory_transactions",
    indices = [Index("vaccineId"), Index("batchId"), Index("vaccinationId")]
)
data class InventoryTransactionEntity(
    @PrimaryKey(autoGenerate = true) val transactionId: Long = 0,
    val vaccineId: String,
    val batchId: String,
    val patientId: String? = null,
    val vaccinationId: String? = null,
    val transactionType: String, // InventoryTransactionType
    val quantity: Int, 
    val previousQuantity: Int,
    val currentQuantity: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val user: String,
    val notes: String? = null
)

// Mappers for compatibility
fun VaccineEntity.toVaccine(totalStock: Int = 0) = Vaccine(
    id = id,
    type = type,
    brandName = brandName,
    companyName = companyName,
    stock = totalStock
)
