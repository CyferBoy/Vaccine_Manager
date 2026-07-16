package com.clinic.neochild.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.clinic.neochild.data.local.preferences.NotificationSettingsManager
import com.clinic.neochild.domain.repository.ReminderRepository
import com.clinic.neochild.domain.repository.VaccineRepository
import com.clinic.neochild.core.utils.PatientUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*

@HiltWorker
class DailySummaryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val reminderRepository: ReminderRepository,
    private val vaccineRepository: VaccineRepository,
    private val settingsManager: NotificationSettingsManager,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = settingsManager.settingsFlow.first()
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())

        // 1. Daily Summary Logic
        if (settings.dailySummaryEnabled && settings.lastSummarySentDate != todayStr) {
            val dueToday = reminderRepository.getDueToday().first().size
            val overdue = reminderRepository.getOverdue().first().size
            val inventory = vaccineRepository.getInventory().first()
            val lowStockCount = inventory.count { it.stock <= settings.lowStockThreshold }

            if (dueToday > 0 || overdue > 0 || lowStockCount > 0) {
                notificationHelper.showDailySummary(dueToday, overdue, lowStockCount)
                settingsManager.markSummarySent(todayStr)
            }
        }

        // 2. Individual Low Stock Logic (Persistent alerts when they first happen)
        if (settings.lowStockEnabled) {
            val inventory = vaccineRepository.getInventory().first()
            for (vaccine in inventory) {
                val isBelowThreshold = vaccine.stock <= settings.lowStockThreshold
                val alreadyNotified = settings.notifiedLowStockVaccines.contains(vaccine.id)

                if (isBelowThreshold && !alreadyNotified) {
                    notificationHelper.showLowStockAlert(vaccine.brandName, vaccine.stock)
                    settingsManager.markLowStockNotified(vaccine.id)
                } else if (!isBelowThreshold && alreadyNotified) {
                    // Reset so we can notify again if it falls below later
                    settingsManager.clearLowStockNotification(vaccine.id)
                }
            }
        }

        return Result.success()
    }
}
