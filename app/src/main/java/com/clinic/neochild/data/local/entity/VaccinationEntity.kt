package com.clinic.neochild.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.clinic.neochild.domain.model.Vaccination

@Entity(
    tableName = "vaccinations",
    indices = [
        Index(value = ["patientId"]), 
        Index(value = ["receiptNumber"]),
        Index(value = ["isSynced"]), 
        Index(value = ["isDeleted"])
    ]
)
data class VaccinationEntity(
    @PrimaryKey val id: String,
    val receiptNumber: String = "",
    val patientId: String,
    val vaccineNames: String,
    val nxtVaccineNames: String,
    val dateGiven: String,
    val nextDueDate: String,
    val cost: Double,
    val cashAmount: Double,
    val onlineAmount: Double,
    val totalPaid: Double,
    val withFees: Boolean,
    val doctorsAcc: Boolean,
    val isDone: Boolean,
    val source: String = "CLINIC",
    val notes: String = "",
    val rescheduleReason: String = "",
    val performedBy: String = "",
    val batchNumbers: String = "",
    val expiryDates: String = "",
    val isSynced: Boolean = true,
    val isDeleted: Boolean = false
)

fun VaccinationEntity.toVaccination() = Vaccination(
    id = id,
    receiptNumber = receiptNumber,
    patientId = patientId,
    vaccineNames = if (vaccineNames.isBlank()) emptyList() else vaccineNames.split(","),
    nxtVaccineNames = if (nxtVaccineNames.isBlank()) emptyList() else nxtVaccineNames.split(","),
    dateGiven = dateGiven,
    nextDueDate = nextDueDate,
    cost = cost,
    cashAmount = cashAmount,
    onlineAmount = onlineAmount,
    totalPaid = totalPaid,
    withFees = withFees,
    doctorsAcc = doctorsAcc,
    isDone = isDone,
    source = source,
    notes = notes,
    rescheduleReason = rescheduleReason,
    performedBy = performedBy,
    batchNumbers = if (batchNumbers.isBlank()) emptyList() else batchNumbers.split(","),
    expiryDates = if (expiryDates.isBlank()) emptyList() else expiryDates.split(",")
)

fun Vaccination.toEntity(isSynced: Boolean = true) = VaccinationEntity(
    id = id,
    receiptNumber = receiptNumber,
    patientId = patientId,
    vaccineNames = vaccineNames.joinToString(","),
    nxtVaccineNames = nxtVaccineNames.joinToString(","),
    dateGiven = dateGiven,
    nextDueDate = nextDueDate,
    cost = cost,
    cashAmount = cashAmount,
    onlineAmount = onlineAmount,
    totalPaid = totalPaid,
    withFees = withFees,
    doctorsAcc = doctorsAcc,
    isDone = isDone,
    source = source,
    notes = notes,
    rescheduleReason = rescheduleReason,
    performedBy = performedBy,
    batchNumbers = batchNumbers.joinToString(","),
    expiryDates = expiryDates.joinToString(","),
    isSynced = isSynced
)
