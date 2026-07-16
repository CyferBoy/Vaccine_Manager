package com.clinic.neochild.domain.model

enum class TimelineEventType {
    REGISTRATION,
    VACCINATION,
    FOLLOW_UP_SCHEDULED,
    FOLLOW_UP_COMPLETED,
    FOLLOW_UP_RESCHEDULED,
    FOLLOW_UP_DISMISSED,
    EXTERNAL_VACCINATION,
    AUDIT_LOG
}

data class TimelineEvent(
    val id: String,
    val type: TimelineEventType,
    val title: String,
    val subtitle: String? = null,
    val timestamp: Long,
    val dateDisplay: String,
    val extraInfo: String? = null,
    val performedBy: String? = null
)
