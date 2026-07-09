package com.clinic.neochild.utils

import com.clinic.neochild.data.model.BorrowedVaccine
import com.clinic.neochild.data.model.Patient
import com.clinic.neochild.data.model.Staff
import com.clinic.neochild.data.model.Vaccination
import com.clinic.neochild.data.model.Vaccine
import com.clinic.neochild.data.model.WasteRecord
import com.google.firebase.firestore.DocumentSnapshot

object FirestoreMappers {

    fun toStaff(doc: DocumentSnapshot): Staff? {
        return try {
            Staff(
                id = doc.id,
                email = doc.getString("email") ?: "",
                name = doc.getString("name") ?: "",
                role = doc.getString("role") ?: "Staff",
                createdAt = doc.getLong("createdAt") ?: 0L
            )
        } catch (_: Exception) {
            null
        }
    }

    fun toWasteRecord(doc: DocumentSnapshot): WasteRecord? {
        return try {
            WasteRecord(
                id = doc.id,
                vaccineId = doc.getString("vaccineId") ?: "",
                brandName = doc.getString("brandName") ?: "",
                batchNumber = doc.getString("batchNumber") ?: "",
                expiryDate = doc.getString("expiryDate") ?: "",
                dateWasted = doc.getString("dateWasted") ?: "",
                reason = doc.getString("reason") ?: "",
                quantity = doc.getLong("quantity")?.toInt() ?: 1
            )
        } catch (_: Exception) {
            null
        }
    }

    fun toBorrowedVaccine(doc: DocumentSnapshot): BorrowedVaccine? {
        return try {
            BorrowedVaccine(
                id = doc.id,
                doctorName = doc.getString("doctorName") ?: "",
                borrowedDate = doc.getString("borrowedDate") ?: "",
                vaccineName = doc.getString("vaccineName") ?: "",
                expiryDate = doc.getString("expiryDate") ?: "",
                batchNumber = doc.getString("batchNumber") ?: "",
                isReturned = doc.getBoolean("isReturned") ?: false,
                returnedDate = doc.getString("returnedDate"),
                type = doc.getString("type") ?: "BY"
            )
        } catch (_: Exception) {
            null
        }
    }

    fun toPatient(doc: DocumentSnapshot): Patient? {
        return try {
            Patient(
                id = doc.id,
                name = doc.getString("name") ?: "",
                phone = doc.getString("phone") ?: "",
                alternatePhone = doc.getString("alternatePhone") ?: "",
                dob = doc.get("dob")?.toString() ?: "",
                gender = doc.getString("gender") ?: "",
                address = doc.getString("address") ?: "",
                registrationDate = doc.getString("registrationDate") ?: ""
            )
        } catch (_: Exception) {
            null
        }
    }

    fun toVaccine(doc: DocumentSnapshot): Vaccine? {
        return try {
            Vaccine(
                id = doc.id,
                type = doc.getString("type") ?: "",
                brandName = doc.getString("brandName") ?: "",
                companyName = doc.getString("companyName") ?: "",
                stock = doc.getLong("stock")?.toInt() ?: 0,
                batchNumber = doc.getString("batchNumber") ?: "",
                expiryDate = doc.getString("expiryDate") ?: "",
                mrp = doc.getDouble("mrp") ?: 0.0,
                netRate = doc.getDouble("netRate") ?: 0.0
            )
        } catch (_: Exception) {
            null
        }
    }

    fun toVaccination(doc: DocumentSnapshot): Vaccination? {
        return try {
            Vaccination(
                id = doc.id,
                patientId = doc.getString("patientId") ?: "",
                vaccineNames = (doc.get("vaccineNames") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
                nxtVaccineNames = (doc.get("nxtVaccineNames") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
                dateGiven = doc.getString("dateGiven") ?: "",
                nextDueDate = doc.getString("nextDueDate") ?: "",
                cost = doc.getDouble("cost") ?: 0.0,
                cashAmount = doc.getDouble("cashAmount") ?: 0.0,
                onlineAmount = doc.getDouble("onlineAmount") ?: 0.0,
                totalPaid = doc.getDouble("totalPaid") ?: 0.0,
                withFees = doc.getBoolean("withFees") ?: false,
                doctorsAcc = doc.getBoolean("doctorsAcc") ?: false,
                isDone = doc.getBoolean("isDone") ?: false,
                source = doc.getString("source") ?: "CLINIC",
                notes = doc.getString("notes") ?: "",
                rescheduleReason = doc.getString("rescheduleReason") ?: "",
                performedBy = doc.getString("performedBy") ?: "",
                batchNumbers = (doc.get("batchNumbers") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
                expiryDates = (doc.get("expiryDates") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
                batchNumber = doc.getString("batchNumber") ?: "",
                expiryDate = doc.getString("expiryDate") ?: ""
            )
        } catch (_: Exception) {
            null
        }
    }
}
