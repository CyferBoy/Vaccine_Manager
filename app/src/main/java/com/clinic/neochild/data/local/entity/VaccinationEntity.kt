package com.clinic.neochild.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.clinic.neochild.data.model.Vaccination

@Entity(
    tableName = "vaccinations",
    indices = [Index(value = ["patientId"]), Index(value = ["isSynced"]), Index(value = ["isDeleted"])]
)
data class VaccinationEntity(
    @PrimaryKey val id: String,
    val patientId: String,
    val vaccineNames: String, // Stored as comma-separated string
    val nxtVaccineNames: String, // Stored as comma-separated string
    val dateGiven: String,
    val nextDueDate: String,
    val cost: Double,
    val cashAmount: Double,
    val onlineAmount: Double,
    val totalPaid: Double,
    val withFees: Boolean,
    val doctorsAcc: Boolean,
    val isDone: Boolean,
    val isSynced: Boolean = true,
    val isDeleted: Boolean = false
)

fun VaccinationEntity.toVaccination() = Vaccination(
    id = id,
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
    isDone = isDone
)

fun Vaccination.toEntity(isSynced: Boolean = true) = VaccinationEntity(
    id = id,
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
    isSynced = isSynced
)
