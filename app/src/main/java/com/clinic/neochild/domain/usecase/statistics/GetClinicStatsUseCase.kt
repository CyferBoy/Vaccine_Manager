package com.clinic.neochild.domain.usecase.statistics

import com.clinic.neochild.domain.repository.ReminderRepository
import com.clinic.neochild.domain.repository.VaccinationRepository
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
    val topVaccines: List<Pair<String, Int>> = emptyList()
)

class GetClinicStatsUseCase @Inject constructor(
    private val vaccinationRepository: VaccinationRepository,
    private val reminderRepository: ReminderRepository
) {
    operator fun invoke(): Flow<ClinicStats> {
        val today = Calendar.getInstance()
        val todayStr = PatientUtils.formatDate(today.time)
        val monthStr = SimpleDateFormat("MMM yyyy", Locale.ENGLISH).format(today.time)

        return combine(
            vaccinationRepository.allVaccinations,
            reminderRepository.getDueToday(),
            reminderRepository.getOverdue()
        ) { vaccinations, dueTodayList, overdueList ->
            val todayList = vaccinations.filter { it.dateGiven == todayStr && it.isDone }
            val monthList = vaccinations.filter { 
                val vDate = PatientUtils.parseDate(it.dateGiven)
                vDate != null && SimpleDateFormat("MMM yyyy", Locale.ENGLISH).format(vDate) == monthStr
            }

            val vaccineCounts = monthList.flatMap { it.vaccineNames }
                .groupBy { it }
                .mapValues { it.value.size }
                .toList()
                .sortedByDescending { it.second }
                .take(5)

            ClinicStats(
                todayVaccinations = todayList.size,
                todayRevenue = todayList.sumOf { it.totalPaid },
                todayCash = todayList.sumOf { it.cashAmount },
                todayOnline = todayList.sumOf { it.onlineAmount },
                monthlyVaccinations = monthList.size,
                monthlyRevenue = monthList.sumOf { it.totalPaid },
                dueToday = dueTodayList.size,
                overdue = overdueList.size,
                topVaccines = vaccineCounts
            )
        }
    }
}
