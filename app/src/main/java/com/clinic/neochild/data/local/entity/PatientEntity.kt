package com.clinic.neochild.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.clinic.neochild.data.model.Patient

@Entity(tableName = "patients")
data class PatientEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String,
    val alternatePhone: String,
    val dob: String,
    val gender: String,
    val address: String = "",
    val registrationDate: String,
    val isSynced: Boolean = true,
    val isDeleted: Boolean = false
)

fun PatientEntity.toPatient() = Patient(
    id = id,
    name = name,
    phone = phone,
    alternatePhone = alternatePhone,
    dob = dob,
    gender = gender,
    address = address,
    registrationDate = registrationDate
)

fun Patient.toEntity(isSynced: Boolean = true) = PatientEntity(
    id = id,
    name = name,
    phone = phone,
    alternatePhone = alternatePhone,
    dob = dob,
    gender = gender,
    address = address,
    registrationDate = registrationDate,
    isSynced = isSynced
)
