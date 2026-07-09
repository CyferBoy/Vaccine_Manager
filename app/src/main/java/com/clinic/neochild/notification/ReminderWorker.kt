package com.clinic.neochild.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.clinic.neochild.data.local.entity.ReminderEntity
import com.clinic.neochild.data.local.preferences.NotificationSettingsManager
import com.clinic.neochild.data.model.ReminderStatus
import com.clinic.neochild.data.model.ReminderType
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

        // 1. Refresh data
        try {
            vaccinationRepository.refreshVaccinations()
            vaccineRepository.refreshInventory()
        } catch (e: Exception) { }

        // 2. Process Vaccinations
        val allVaccinations = vaccinationRepository.allVaccinations.first()
        val unsatisfiedRequirements = ReminderEngine.getUnsatisfiedRequirements(allVaccinations)
        
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayTime = today.time
        
        val tomorrow = Calendar.getInstance().apply {
            time = today.time
            add(Calendar.DAY_OF_YEAR, 1)
        }.time

        var dueTodayCount = 0
        
        // Group by patient to avoid duplicate notifications per person
        val requirementsByPatient = unsatisfiedRequirements.groupBy { it.patientId }

        for ((patientId, requirements) in requirementsByPatient) {
            val patient = patientRepository.getPatientById(patientId) ?: continue

            // Find requirements that are due or overdue
            val dueNow = requirements.filter { req ->
                req.dueDate.before(tomorrow) || req.dueDate == todayTime
            }

            if (dueNow.isNotEmpty()) {
                val oldestReq = dueNow.minByOrNull { it.dueDate } ?: continue
                val vaccineList = dueNow.joinToString(", ") { it.vaccineName }
                
                when {
                    // Due Today
                    oldestReq.dueDate == todayTime -> {
                        dueTodayCount++
                        checkAndNotifyPatient(
                            patientId = patient.id,
                            patientName = patient.name,
                            vaccinationId = oldestReq.originalVisitId,
                            vaccineName = vaccineList,
                            type = ReminderType.DUE_TODAY,
                            dueDate = PatientUtils.formatDate(oldestReq.dueDate)
                        )
                    }
                    // Tomorrow
                    oldestReq.dueDate == tomorrow -> {
                        if (settings.reminderDaysBefore == 1) {
                            checkAndNotifyPatient(
                                patientId = patient.id,
                                patientName = patient.name,
                                vaccinationId = oldestReq.originalVisitId,
                                vaccineName = vaccineList,
                                type = ReminderType.TOMORROW,
                                dueDate = PatientUtils.formatDate(oldestReq.dueDate)
                            )
                        }
                    }
                    // Overdue
                    oldestReq.dueDate.before(todayTime) -> {
                        val diff = todayTime.time - oldestReq.dueDate.time
                        val days = (diff / (1000 * 60 * 60 * 24)).toInt()
                        if (days > 0 && days % settings.overdueFrequencyDays == 0) {
                            checkAndNotifyPatient(
                                patientId = patient.id,
                                patientName = patient.name,
                                vaccinationId = oldestReq.originalVisitId,
                                vaccineName = vaccineList,
                                type = ReminderType.OVERDUE,
                                dueDate = PatientUtils.formatDate(oldestReq.dueDate),
                                extraInfo = "Delayed by $days days"
                            )
                        }
                    }
                }
            }
        }

        if (dueTodayCount > 1) {
            notificationHelper.showVaccinationNotification(
                100, 
                "Vaccinations Due Today", 
                "$dueTodayCount patients are due today. Tap to open."
            )
        }

        // 3. Process Inventory
        val inventory = vaccineRepository.getInventory().first()
        for (item in inventory) {
            // Out of stock
            if (item.stock == 0) {
                checkAndNotifyInventory(item.id, item.brandName, ReminderType.OUT_OF_STOCK, "Out of Stock")
            } else if (item.stock <= settings.lowStockThreshold) {
                // Low stock
                checkAndNotifyInventory(item.id, item.brandName, ReminderType.LOW_STOCK, "Remaining: ${item.stock} doses")
            }

            // Expiry
            val expDate = PatientUtils.parseDate(item.expiryDate) ?: continue
            val expCal = Calendar.getInstance().apply { time = expDate }
            val diff = expCal.timeInMillis - today.timeInMillis
            val daysToExpiry = (diff / (1000 * 60 * 60 * 24)).toInt()

            if (daysToExpiry <= settings.expiryDaysBefore && daysToExpiry >= 0) {
                checkAndNotifyInventory(item.id, item.brandName, ReminderType.EXPIRY, "Expires on ${item.expiryDate}")
            }
        }

        return Result.success()
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
