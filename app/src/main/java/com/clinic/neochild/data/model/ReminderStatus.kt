package com.clinic.neochild.data.model

enum class ReminderStatus {
    ACTIVE,
    COMPLETED,
    RESCHEDULED,
    EXTERNAL,
    DISMISSED,
    MISSED,
    SENT // Kept for notification tracking
}
