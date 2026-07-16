package com.clinic.neochild.core.utils

import com.clinic.neochild.core.common.Constants
import com.clinic.neochild.domain.model.Vaccination
import java.text.SimpleDateFormat
import java.util.*

object PatientUtils {
    
    /**
     * Returns a user-friendly age string (e.g., "5 Years", "2 Months", "3 Weeks").
     */
    fun calculateAgeLabel(dob: String): String? {
        try {
            val birthDate = parseDate(dob) ?: return null
            val today = Calendar.getInstance()
            val birth = Calendar.getInstance()
            birth.time = birthDate

            var years = today[Calendar.YEAR] - birth[Calendar.YEAR]
            var months = today[Calendar.MONTH] - birth[Calendar.MONTH]
            
            if (today[Calendar.DAY_OF_MONTH] < birth[Calendar.DAY_OF_MONTH]) {
                months--
            }
            
            if (months < 0) {
                years--
                months += 12
            }

            if (years < 0) return null

            return when {
                years > 0 -> {
                    if (months > 0) "$years years $months months"
                    else "$years years"
                }
                months > 0 -> "$months months"
                else -> {
                    val diffMs = today.timeInMillis - birth.timeInMillis
                    val diffDays = (diffMs / (1000 * 60 * 60 * 24)).toInt()
                    val weeks = diffDays / 7
                    if (weeks <= 1) "1 week" else "$weeks weeks"
                }
            }
        } catch (_: Exception) {
            return null
        }
    }

    /**
     * Calculates age and returns the value and the unit (Years/Months/Weeks).
     * Used for pre-filling the Add/Edit Patient screen.
     */
    fun calculateDetailedAge(dob: String): Pair<Int, String> {
        try {
            val birthDate = parseDate(dob) ?: return 0 to "Years"
            val today = Calendar.getInstance()
            val birth = Calendar.getInstance()
            birth.time = birthDate

            val diffMs = today.timeInMillis - birth.timeInMillis
            val diffDays = (diffMs / (1000 * 60 * 60 * 24)).toInt()

            if (diffDays < 30) {
                val weeks = diffDays / 7
                return if (weeks > 0) weeks to "Weeks" else 0 to "Weeks"
            }
            
            val years = today[Calendar.YEAR] - birth[Calendar.YEAR]
            val months = today[Calendar.MONTH] - birth[Calendar.MONTH]
            val totalMonths = (years * 12) + months
            
            return if (totalMonths < 12) {
                totalMonths to "Months"
            } else {
                var ageYears = years
                if (today[Calendar.DAY_OF_YEAR] < birth[Calendar.DAY_OF_YEAR]) {
                    ageYears--
                }
                ageYears to "Years"
            }
        } catch (_: Exception) {
            return 0 to "Years"
        }
    }

    /**
     * Tries to parse a date string using multiple common formats.
     */
    fun parseDate(dateStr: String): Date? {
        if (dateStr.isBlank()) return null
        val formats = listOf(Constants.DATE_FORMAT, "d/M/yyyy", "dd/MM/yyyy", "yyyy-MM-dd")
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.ENGLISH)
                sdf.isLenient = false
                return sdf.parse(dateStr)
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * Formats a Date object to the standard app display format.
     */
    fun formatDate(date: Date): String {
        return SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(date)
    }

    /**
     * Standardizes any date string to the current app format (e.g. 9 May 2026).
     */
    fun formatDateForDisplay(dateStr: String): String {
        val date = parseDate(dateStr) ?: return dateStr
        return SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(date)
    }

    /**
     * Removes parentheses from vaccine names if they exist (e.g., "Hepatitis B (HepB)" -> "HepB").
     */
    fun cleanVaccineName(name: String): String {
        return if (name.contains("(") && name.endsWith(")")) {
            name.substringAfter("(").substringBeforeLast(")").trim()
        } else {
            name
        }
    }

    /**
     * Legacy Logic: A vaccination is "actually pending" if:
     * 1. isDone is false
     * 2. There is NO other vaccination record for the same patient that was given AFTER this record's dateGiven.
     *    (If a patient visits and a record is added, it supercedes all previous reminders/pending items).
     */
    fun getPendingVaccinations(allVaccinations: List<Vaccination>): List<Vaccination> {
        return allVaccinations.filter { v ->
            if (v.isDone) return@filter false
            if (v.nextDueDate.isBlank()) return@filter false
            
            val thisDateGiven = parseDate(v.dateGiven)
            
            // Check if any record for the same patient has a strictly later dateGiven
            val hasNewerRecord = allVaccinations.any { other ->
                if (other.id == v.id || other.patientId != v.patientId) return@any false
                val otherDateGiven = parseDate(other.dateGiven)
                otherDateGiven != null && thisDateGiven != null && otherDateGiven.after(thisDateGiven)
            }
            
            !hasNewerRecord
        }
    }

    /**
     * Legacy Logic: Filters pending vaccinations based on a string filter (e.g., "Overdue", "Today").
     * Note: This is now partially superseded by ReminderEngine but kept for UI compatibility.
     */
    fun filterVaccinationsByPeriod(
        pendingVaccinations: List<Vaccination>,
        filter: String,
    ): List<Vaccination> {
        val calendar = Calendar.getInstance()
        calendar[Calendar.HOUR_OF_DAY] = 0
        calendar[Calendar.MINUTE] = 0
        calendar[Calendar.SECOND] = 0
        calendar[Calendar.MILLISECOND] = 0
        val todayStart = calendar.time
        
        val weekEnd = Calendar.getInstance().apply { 
            add(Calendar.DAY_OF_YEAR, 7)
            this[Calendar.HOUR_OF_DAY] = 23
            this[Calendar.MINUTE] = 59
            this[Calendar.SECOND] = 59
            this[Calendar.MILLISECOND] = 999 
        }.time

        val lastMonthCal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
        val lastMonth = lastMonthCal[Calendar.MONTH]
        val lastMonthYear = lastMonthCal[Calendar.YEAR]

        return pendingVaccinations.filter { v ->
            val dueDate = parseDate(v.nextDueDate) ?: return@filter false
            when (filter) {
                "Overdue" -> dueDate.before(todayStart)
                "Previous Month" -> {
                    val cal = Calendar.getInstance().apply { time = dueDate }
                    cal[Calendar.MONTH] == lastMonth && cal[Calendar.YEAR] == lastMonthYear
                }
                "Today" -> dueDate == todayStart
                "This Week" -> (dueDate == todayStart || dueDate.after(todayStart)) && (dueDate == weekEnd || dueDate.before(weekEnd))
                "Upcoming" -> dueDate.after(weekEnd)
                else -> false
            }
        }.let { list ->
            if (filter == "Overdue" || filter == "Previous Month") {
                list.sortedByDescending { parseDate(it.nextDueDate) }
            } else {
                list.sortedBy { parseDate(it.nextDueDate) }
            }
        }
    }
}
