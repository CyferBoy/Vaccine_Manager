package com.clinic.neochild.data.remote.mapper

import com.clinic.neochild.core.model.BorrowedVaccine
import com.clinic.neochild.data.local.entity.*
import com.clinic.neochild.domain.model.*
import com.google.firebase.firestore.DocumentSnapshot
import java.util.Date

object FirestoreMappers {

    fun toStaff(doc: DocumentSnapshot): Staff? {
        return try {
            Staff(
                id = doc.id,
                email = doc.getString("email") ?: "",
                name = doc.getString("name") ?: "",
                role = doc.getString("role") ?: "Staff",
                fcmToken = doc.getString("fcmToken"),
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
                batchId = doc.getString("batchId") ?: "",
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
                patientClinicId = doc.getString("patientClinicId") ?: "",
                name = doc.getString("name") ?: "",
                parentName = doc.getString("parentName") ?: "",
                phone = doc.getString("phone") ?: "",
                alternatePhone = doc.getString("alternatePhone") ?: "",
                dob = doc.get("dob")?.toString() ?: "",
                gender = doc.getString("gender") ?: "",
                village = doc.getString("village") ?: "",
                address = doc.getString("address") ?: "",
                registrationDate = doc.get("registrationDate")?.toString() ?: "",
                isDeleted = doc.getBoolean("isDeleted") ?: false
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

    fun toVaccineEntity(doc: DocumentSnapshot): VaccineEntity? {
        return try {
            VaccineEntity(
                id = doc.id,
                type = doc.getString("type") ?: "",
                brandName = doc.getString("brandName") ?: "",
                companyName = doc.getString("companyName") ?: ""
            )
        } catch (_: Exception) {
            null
        }
    }

    fun toVaccineBatchEntity(doc: DocumentSnapshot): VaccineBatchEntity? {
        return try {
            VaccineBatchEntity(
                batchId = doc.id,
                vaccineId = doc.getString("vaccineId") ?: "",
                batchNumber = doc.getString("batchNumber") ?: "",
                manufacturer = doc.getString("manufacturer") ?: "",
                purchaseDate = doc.getString("purchaseDate") ?: "",
                expiryDate = doc.getString("expiryDate") ?: "",
                purchaseQuantity = doc.getLong("purchaseQuantity")?.toInt() ?: 0,
                remainingQuantity = doc.getLong("remainingQuantity")?.toInt() ?: 0,
                supplier = doc.getString("supplier") ?: "",
                purchaseCost = doc.getDouble("purchaseCost") ?: 0.0,
                sellingPrice = doc.getDouble("sellingPrice") ?: 0.0,
                status = doc.getString("status") ?: "ACTIVE"
            )
        } catch (_: Exception) {
            null
        }
    }

    fun toVaccination(doc: DocumentSnapshot): Vaccination? {
        return try {
            val isDone = doc.getBoolean("isDone") ?: doc.getBoolean("done") ?: false
            
            fun getList(field: String): List<String> {
                val value = doc.get(field)
                return when (value) {
                    is List<*> -> value.mapNotNull { it?.toString() }
                    is String -> if (value.isBlank()) emptyList() else value.split(",").map { it.trim() }
                    else -> emptyList()
                }
            }

            Vaccination(
                id = doc.id,
                receiptNumber = doc.getString("receiptNumber") ?: "",
                patientId = doc.getString("patientId") ?: "",
                vaccineNames = getList("vaccineNames"),
                vaccineIds = getList("vaccineIds"),
                nxtVaccineNames = getList("nxtVaccineNames"),
                dateGiven = doc.getString("dateGiven") ?: "",
                nextDueDate = doc.getString("nextDueDate") ?: "",
                cost = (doc.get("cost") as? Number)?.toDouble() ?: 0.0,
                cashAmount = (doc.get("cashAmount") as? Number)?.toDouble() ?: 0.0,
                onlineAmount = (doc.get("onlineAmount") as? Number)?.toDouble() ?: 0.0,
                totalPaid = (doc.get("totalPaid") as? Number)?.toDouble() ?: 0.0,
                withFees = doc.getBoolean("withFees") ?: false,
                doctorsAcc = doc.getBoolean("doctorsAcc") ?: false,
                isDone = isDone,
                source = doc.getString("source") ?: "CLINIC",
                notes = doc.getString("notes") ?: "",
                rescheduleReason = doc.getString("rescheduleReason") ?: "",
                performedBy = doc.getString("performedBy") ?: "",
                batchNumbers = getList("batchNumbers"),
                expiryDates = getList("expiryDates")
            )
        } catch (_: Exception) {
            null
        }
    }

    fun toReminderEntity(doc: DocumentSnapshot): ReminderEntity? {
        return try {
            ReminderEntity(
                id = doc.id.toLongOrNull() ?: 0L,
                patientId = doc.getString("patientId") ?: "",
                originalVisitId = doc.getString("originalVisitId") ?: "",
                vaccineName = doc.getString("vaccineName") ?: "",
                dueDate = doc.getString("dueDate") ?: "",
                status = doc.getString("status") ?: "ACTIVE",
                reminderDate = doc.getString("reminderDate"),
                priority = doc.getString("priority") ?: "NORMAL",
                reminderEnabled = doc.getBoolean("reminderEnabled") ?: true,
                category = doc.getString("category") ?: "VACCINATION",
                notes = doc.getString("notes"),
                completionDate = doc.getLong("completionDate"),
                performedBy = doc.getString("performedBy"),
                dismissalDate = doc.getLong("dismissalDate"),
                dismissalReason = doc.getString("dismissalReason"),
                externalDate = doc.getString("externalDate"),
                source = doc.getString("source"),
                lastReminderTime = doc.getLong("lastReminderTime") ?: 0L,
                notificationSent = doc.getBoolean("notificationSent") ?: false,
                createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
                isSynced = true
            )
        } catch (_: Exception) {
            null
        }
    }

    // Legacy Support Wrappers
    fun toDueReminderEntity(doc: DocumentSnapshot) = toReminderEntity(doc)
    fun toCompletedReminderEntity(doc: DocumentSnapshot) = toReminderEntity(doc)
    fun toDismissedReminderEntity(doc: DocumentSnapshot) = toReminderEntity(doc)
    fun toExternalReminderEntity(doc: DocumentSnapshot) = toReminderEntity(doc)

    fun toFinanceEntity(doc: DocumentSnapshot): FinanceEntity? {
        return try {
            FinanceEntity(
                id = doc.id.toLongOrNull() ?: 0L,
                timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                type = doc.getString("type") ?: "INCOME",
                category = doc.getString("category") ?: "OTHER",
                amount = doc.getDouble("amount") ?: 0.0,
                currency = doc.getString("currency") ?: "INR",
                paymentMethod = doc.getString("paymentMethod") ?: "CASH",
                patientId = doc.getString("patientId"),
                visitId = doc.getString("visitId"),
                referenceNumber = doc.getString("referenceNumber"),
                remarks = doc.getString("remarks"),
                recordedBy = doc.getString("recordedBy") ?: "SYSTEM",
                isSynced = true
            )
        } catch (_: Exception) {
            null
        }
    }

    fun toBorrowEntity(doc: DocumentSnapshot): BorrowEntity? {
        return try {
            BorrowEntity(
                id = doc.id,
                doctorName = doc.getString("doctorName") ?: "",
                vaccineId = doc.getString("vaccineId") ?: "",
                batchId = doc.getString("batchId") ?: "",
                borrowedDate = doc.getString("borrowedDate") ?: "",
                quantity = doc.getLong("quantity")?.toInt() ?: 1,
                isReturned = doc.getBoolean("isReturned") ?: false,
                returnedDate = doc.getString("returnedDate"),
                type = doc.getString("type") ?: "BY",
                notes = doc.getString("notes"),
                isSynced = true
            )
        } catch (_: Exception) {
            null
        }
    }

    fun toStaffEntity(doc: DocumentSnapshot): StaffEntity? {
        return try {
            StaffEntity(
                id = doc.id,
                name = doc.getString("name") ?: "",
                email = doc.getString("email") ?: "",
                role = doc.getString("role") ?: "Staff",
                department = doc.getString("department"),
                permissions = doc.getString("permissions"),
                fcmToken = doc.getString("fcmToken"),
                isActive = doc.getBoolean("isActive") ?: true,
                createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                lastActive = doc.getLong("lastActive") ?: System.currentTimeMillis()
            )
        } catch (_: Exception) {
            null
        }
    }

    fun toUserEntity(doc: DocumentSnapshot): UserEntity? {
        return try {
            UserEntity(
                id = doc.id,
                email = doc.getString("email") ?: "",
                name = doc.getString("name") ?: "",
                biometricEnabled = doc.getBoolean("biometricEnabled") ?: false,
                pinHash = doc.getString("pinHash"),
                lastLogin = doc.getLong("lastLogin") ?: 0L,
                fcmToken = doc.getString("fcmToken"),
                devices = doc.getString("devices"),
                securityStamp = doc.getString("securityStamp") ?: ""
            )
        } catch (_: Exception) {
            null
        }
    }

    fun toPatientNoteEntity(doc: DocumentSnapshot): PatientNotesEntity? {
        return try {
            PatientNotesEntity(
                id = doc.id.toLongOrNull() ?: 0L,
                patientId = doc.getString("patientId") ?: "",
                content = doc.getString("content") ?: "",
                author = doc.getString("author") ?: "",
                timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                isSynced = true,
                isDeleted = doc.getBoolean("isDeleted") ?: false
            )
        } catch (_: Exception) {
            null
        }
    }
}
