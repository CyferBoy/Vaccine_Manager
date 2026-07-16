package com.clinic.neochild.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.clinic.neochild.data.local.entity.ReminderEntity
import com.clinic.neochild.data.local.preferences.NotificationSettingsManager
import com.clinic.neochild.data.model.ReminderStatus
import com.clinic.neochild.data.model.ReminderType
import com.clinic.neochild.data.model.Vaccination
import com.clinic.neochild.domain.repository.PatientRepository
import com.clinic.neochild.domain.repository.ReminderRepository
import com.clinic.neochild.domain.repository.VaccinationRepository
import com.clinic.neochild.domain.repository.VaccineRepository
import com.clinic.neochild.utils.PatientUtils
import com.clinic.neochild.utils.ReminderEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.*

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val patientRepository: PatientRepository,
    private val vaccinationRepository: VaccinationRepository,
    private val vaccineRepository: VaccineRepository,
    private val reminderRepository: ReminderRepository,
    private val settingsManager: NotificationSettingsManager,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = settingsManager.settingsFlow.first()
        if (!settings.enabled) return Result.success()

        // 1. Trigger Data Refresh (Optional, depending on clinic sync needs)
        try {
            reminderRepository.syncWithRemote()
        } catch (e: Exception) { }

        // 2. Process Vaccinations - Simple Delegation to Repository
        val dueToday = reminderRepository.getDueToday().first()
        val dueTomorrow = reminderRepository.getDueTomorrow().first()
        val overdue = reminderRepository.getOverdue().first()

        val patientsNotified = mutableSetOf<String>()

        // Notify for Due Today
        dueToday.forEach { vacc ->
            if (patientsNotified.add(vacc.patientId)) {
                notifyForVaccination(vacc, ReminderType.DUE_TODAY)
            }
        }

        // Notify for Tomorrow
        if (settings.reminderDaysBefore == 1) {
            dueTomorrow.forEach { vacc ->
                if (patientsNotified.add(vacc.patientId)) {
                    notifyForVaccination(vacc, ReminderType.TOMORROW)
                }
            }
        }

        // Notify for Overdue (based on configured frequency)
        overdue.forEach { vacc ->
            if (patientsNotified.add(vacc.patientId)) {
                val dueDate = PatientUtils.parseDate(vacc.nextDueDate) ?: return@forEach
                val diff = System.currentTimeMillis() - dueDate.time
                val days = (diff / (1000 * 60 * 60 * 24)).toInt()
                
                if (days > 0 && days % settings.overdueFrequencyDays == 0) {
                    notifyForVaccination(vacc, ReminderType.OVERDUE, "Delayed by $days days")
                }
            }
        }

        // Summary notification if multiple due
        if (dueToday.size > 1) {
            notificationHelper.showVaccinationNotification(
                100, 
                "Vaccinations Due Today", 
                "${dueToday.size} patients are due today. Tap to open."
            )
        }

        // 3. Process Inventory (Delegated to specialized repo if exists, otherwise here)
        processInventoryAlerts(settings)

        return Result.success()
    }

    private suspend fun notifyForVaccination(
        vacc: Vaccination,
        type: ReminderType,
        extraInfo: String = ""
    ) {
        val patient = patientRepository.getPatientById(vacc.patientId) ?: return
        val vaccineName = vacc.nxtVaccineNames.firstOrNull() ?: "Vaccination"
        
        checkAndNotifyPatient(
            patientId = patient.id,
            patientName = patient.name,
            vaccinationId = vacc.id,
            vaccineName = vaccineName,
            type = type,
            dueDate = vacc.nextDueDate,
            extraInfo = extraInfo
        )
    }

    private suspend fun processInventoryAlerts(settings: com.clinic.neochild.data.local.preferences.NotificationSettings) {
        val inventory = vaccineRepository.getInventory().first()
        for (item in inventory) {
            if (item.stock == 0) {
                checkAndNotifyInventory(item.id, item.brandName, ReminderType.OUT_OF_STOCK, "Out of Stock")
            } else if (item.stock <= settings.lowStockThreshold) {
                checkAndNotifyInventory(item.id, item.brandName, ReminderType.LOW_STOCK, "Remaining: ${item.stock} doses")
            }
            
            val expDate = PatientUtils.parseDate(item.expiryDate) ?: continue
            val diff = expDate.time - System.currentTimeMillis()
            val daysToExpiry = (diff / (1000 * 60 * 60 * 24)).toInt()

            if (daysToExpiry <= settings.expiryDaysBefore && daysToExpiry >= 0) {
                checkAndNotifyInventory(item.id, item.brandName, ReminderType.EXPIRY, "Expires on ${item.expiryDate}")
            }
        }
    }

    private suspend fun checkAndNotifyPatient(
        patientId: String, 
        patientName: String,
        vaccinationId: String,
        vaccineName: String,
        type: ReminderType,
        dueDate: String,
        extraInfo: String = ""
    ) {
        val existing = reminderRepository.getPendingPatientReminder(patientId, type)
        if (existing != null && existing.lastReminderTime > System.currentTimeMillis() - 12 * 60 * 60 * 1000) {
            // Already notified in last 12 hours
            return
        }

        val title = when(type) {
            ReminderType.DUE_TODAY -> "Vaccination Due Today"
            ReminderType.TOMORROW -> "Vaccination Reminder"
            ReminderType.OVERDUE -> "Overdue Vaccination"
            else -> "Reminder"
        }
        
        val content = "Patient: $patientName\nVaccine: $vaccineName\n$extraInfo".trim()

        val reminder = existing?.copy(
            lastReminderTime = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        ) ?: ReminderEntity(
            patientId = patientId,
            vaccineId = vaccinationId,
            type = type.name,
            dueDate = dueDate,
            status = ReminderStatus.SENT.name,
            notificationSent = true,
            lastReminderTime = System.currentTimeMillis()
        )
        
        val id = reminderRepository.insertReminder(reminder)
        
        notificationHelper.showVaccinationNotification(
            patientId.hashCode() + type.ordinal, 
            title, 
            content, 
            patientId, 
            vaccinationId, 
            if (existing != null) existing.id else id
        )
    }

    private suspend fun checkAndNotifyInventory(
        vaccineId: String,
        brandName: String,
        type: ReminderType,
        content: String
    ) {
        val existing = reminderRepository.getPendingVaccineReminder(vaccineId, type)
        if (existing != null && existing.lastReminderTime > System.currentTimeMillis() - 24 * 60 * 60 * 1000) {
            return
        }

        val title = when(type) {
            ReminderType.OUT_OF_STOCK -> "Out of Stock"
            ReminderType.LOW_STOCK -> "Low Stock"
            ReminderType.EXPIRY -> "Vaccine Expiry Reminder"
            else -> "Inventory Alert"
        }

        if (type == ReminderType.EXPIRY) {
            notificationHelper.showExpiryAlert(vaccineId.hashCode() + type.ordinal, title, "$brandName: $content")
        } else {
            notificationHelper.showInventoryAlert(vaccineId.hashCode() + type.ordinal, title, "$brandName: $content")
        }

        val reminder = existing?.copy(
            lastReminderTime = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        ) ?: ReminderEntity(
            patientId = null,
            vaccineId = vaccineId,
            type = type.name,
            dueDate = "",
            status = ReminderStatus.SENT.name,
            notificationSent = true,
            lastReminderTime = System.currentTimeMillis()
        )
        
        reminderRepository.insertReminder(reminder)
    }
}
