package com.clinic.neochild.core.utils

import java.util.*
import java.util.concurrent.TimeUnit

sealed class DateCategory {
    data class Overdue(val days: Int) : DateCategory()
    object Yesterday : DateCategory()
    object Today : DateCategory()
    object Tomorrow : DateCategory()
    data class Future(val dateStr: String) : DateCategory()
}

object DateClassifier {

    /**
     * Helper to get a normalized Calendar at start of day.
     */
    fun getTodayStart(): Calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    /**
     * Classifies a date string into clinic-friendly categories.
     * Uses device local time.
     */
    fun classify(dateStr: String, todayStart: Calendar = getTodayStart()): DateCategory {
        val targetDate = PatientUtils.parseDate(dateStr) ?: return DateCategory.Future(dateStr)
        
        val target = Calendar.getInstance().apply {
            time = targetDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val diffMs = target.timeInMillis - todayStart.timeInMillis
        val diffDays = TimeUnit.MILLISECONDS.toDays(diffMs).toInt()

        return when {
            diffDays < -1 -> DateCategory.Overdue(-diffDays)
            diffDays == -1 -> DateCategory.Yesterday
            diffDays == 0 -> DateCategory.Today
            diffDays == 1 -> DateCategory.Tomorrow
            else -> DateCategory.Future(PatientUtils.formatDateForDisplay(dateStr))
        }
    }

    /**
     * Formats the classification for display.
     */
    fun formatDisplay(dateStr: String, todayStart: Calendar = getTodayStart()): String {
        return when (val category = classify(dateStr, todayStart)) {
            is DateCategory.Overdue -> "Overdue (${category.days} days)"
            is DateCategory.Yesterday -> "Yesterday"
            is DateCategory.Today -> "Today"
            is DateCategory.Tomorrow -> "Tomorrow"
            is DateCategory.Future -> category.dateStr
        }
    }

    /**
     * Unified comparator for sorting dates as requested:
     * 1. Overdue (oldest first)
     * 2. Yesterday
     * 3. Today
     * 4. Tomorrow
     * 5. Future (nearest first)
     */
    fun getSortWeight(dateStr: String): Long {
        val targetDate = PatientUtils.parseDate(dateStr) ?: return Long.MAX_VALUE
        return targetDate.time
    }
}
