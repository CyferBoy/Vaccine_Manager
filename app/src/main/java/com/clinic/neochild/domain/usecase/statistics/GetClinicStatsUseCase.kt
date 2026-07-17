package com.clinic.neochild.domain.usecase.statistics

import com.clinic.neochild.domain.repository.ReminderRepository
import com.clinic.neochild.domain.repository.VaccinationRepository
import com.clinic.neochild.domain.repository.InventoryRepository
import com.clinic.neochild.core.utils.PatientUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class ClinicStats(
    val todayVaccinations: Int = 0,
    val todayRevenue: Double = 0.0,
    val todayCash: Double = 0.0,
    val todayOnline: Double = 0.0,
    val monthlyVaccinations: Int = 0,
    val monthlyRevenue: Double = 0.0,
    val dueToday: Int = 0,
    val overdue: Int = 0,
    val lowStockCount: Int = 0,
    val topVaccines: List<Pair<String, Int>> = emptyList()
)

/**
 * Calculates high-level clinic performance metrics.
 * Uses optimized database queries to minimize memory pressure.
 */
class GetClinicStatsUseCase @Inject constructor(
    private val vaccinationRepository: VaccinationRepository,
    private val reminderRepository: ReminderRepository,
    private val inventoryRepository: InventoryRepository
) {
    operator fun invoke(): Flow<ClinicStats> {
        val today = Calendar.getInstance()
        val todayStr = PatientUtils.formatDate(today.time)
        
        // Month pattern for SQLite LIKE: "% May 2026"
        val monthPattern = "% ${SimpleDateFormat("MMM yyyy", Locale.ENGLISH).format(today.time)}"

        return combine(
            vaccinationRepository.getTodayCount(todayStr),
            vaccinationRepository.getTodayRevenue(todayStr),
            vaccinationRepository.getTodayCash(todayStr),
            vaccinationRepository.getTodayOnline(todayStr),
            vaccinationRepository.getMonthlyCount(monthPattern),
            vaccinationRepository.getMonthlyRevenue(monthPattern),
            reminderRepository.getDashboardStats(),
            inventoryRepository.getInventoryItems(),
            vaccinationRepository.getVaccineNamesForMonth(monthPattern)
        ) { args: Array<Any?> ->
            val todayCount = args[0] as Int
            val todayRevenue = args[1] as? Double ?: 0.0
            val todayCash = args[2] as? Double ?: 0.0
            val todayOnline = args[3] as? Double ?: 0.0
            val monthlyCount = args[4] as Int
            val monthlyRevenue = args[5] as? Double ?: 0.0
            val reminderStats = args[6] as com.clinic.neochild.domain.repository.ReminderStats
            val inventory = args[7] as List<com.clinic.neochild.domain.model.InventoryItem>
            val vaccineNamesList = args[8] as List<String>

            val topVaccines = vaccineNamesList.flatMap { names -> names.split(",").map { it.trim() } }
                .filter { it.isNotEmpty() }
                .groupBy { it }
                .mapValues { it.value.size }
                .toList()
                .sortedByDescending { it.second }
                .take(5)

            ClinicStats(
                todayVaccinations = todayCount,
                todayRevenue = todayRevenue,
                todayCash = todayCash,
                todayOnline = todayOnline,
                monthlyVaccinations = monthlyCount,
                monthlyRevenue = monthlyRevenue,
                dueToday = reminderStats.dueToday,
                overdue = reminderStats.overdue,
                lowStockCount = inventory.count { it.stock <= it.threshold },
                topVaccines = topVaccines
            )
        }
    }
}
