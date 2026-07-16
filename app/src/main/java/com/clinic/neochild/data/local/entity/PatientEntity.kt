package com.clinic.neochild.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.clinic.neochild.domain.model.Patient

@Entity(
    tableName = "patients",
    indices = [Index(value = ["isSynced"]), Index(value = ["isDeleted"])]
)
data class PatientEntity(
    @PrimaryKey val id: String,
    val patientClinicId: String = "",
    val name: String,
    val parentName: String = "",
    val phone: String,
    val alternatePhone: String,
    val dob: String,
    val gender: String,
    val village: String = "",
    val address: String = "",
    val registrationDate: String,
    val isSynced: Boolean = true,
    val isDeleted: Boolean = false
)

fun PatientEntity.toPatient() = Patient(
    id = id,
    patientClinicId = patientClinicId,
    name = name,
    parentName = parentName,
    phone = phone,
    alternatePhone = alternatePhone,
    dob = dob,
    gender = gender,
    village = village,
    address = address,
    registrationDate = registrationDate
)

fun Patient.toEntity(isSynced: Boolean = true) = PatientEntity(
    id = id,
    patientClinicId = patientClinicId,
    name = name,
    parentName = parentName,
    phone = phone,
    alternatePhone = alternatePhone,
    dob = dob,
    gender = gender,
    village = village,
    address = address,
    registrationDate = registrationDate,
    isSynced = isSynced
)
