package com.clinic.neochild.domain.usecase.patient

import com.clinic.neochild.data.local.entity.ReminderAuditEntity
import com.clinic.neochild.domain.model.Patient
import com.clinic.neochild.domain.model.Vaccination
import com.clinic.neochild.domain.model.TimelineEvent
import com.clinic.neochild.domain.model.TimelineEventType
import com.clinic.neochild.domain.repository.ReminderRepository
import com.clinic.neochild.core.utils.PatientUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class GetPatientTimelineUseCase @Inject constructor(
    private val reminderRepository: ReminderRepository
) {
    operator fun invoke(
        patient: Patient,
        vaccinations: List<Vaccination>
    ): Flow<List<TimelineEvent>> {
        return reminderRepository.getAuditTrail(patient.id).combine(
            reminderRepository.getPatientFollowUps(patient.id)
        ) { audits, followUps ->
            val events = mutableListOf<TimelineEvent>()

            // 1. Registration
            val regDate = PatientUtils.parseDate(patient.registrationDate)
            events.add(TimelineEvent(
                id = "reg_${patient.id}",
                type = TimelineEventType.REGISTRATION,
                title = "Patient Registered",
                subtitle = "Clinic ID: ${patient.patientClinicId}",
                timestamp = regDate?.time ?: 0L,
                dateDisplay = patient.registrationDate
            ))

            // 2. Vaccinations
            vaccinations.forEach { v ->
                val vDate = PatientUtils.parseDate(v.dateGiven)
                events.add(TimelineEvent(
                    id = v.id,
                    type = if (v.source == "CLINIC") TimelineEventType.VACCINATION else TimelineEventType.EXTERNAL_VACCINATION,
                    title = if (v.source == "CLINIC") "Vaccinated in Clinic" else "Vaccinated Elsewhere",
                    subtitle = v.vaccineNames.joinToString(", "),
                    timestamp = vDate?.time ?: 0L,
                    dateDisplay = v.dateGiven,
                    performedBy = v.performedBy,
                    extraInfo = "Receipt: ${v.receiptNumber}"
                ))
            }

            // 3. Audits (Manual Actions)
            audits.forEach { audit ->
                events.add(TimelineEvent(
                    id = "audit_${audit.auditId}",
                    type = TimelineEventType.AUDIT_LOG,
                    title = "Follow-up ${audit.action.lowercase().replaceFirstChar { it.uppercase() }}",
                    subtitle = "${audit.vaccineName} (${audit.newStatus})",
                    timestamp = audit.timestamp,
                    dateDisplay = PatientUtils.formatDate(java.util.Date(audit.timestamp)),
                    performedBy = audit.performedBy,
                    extraInfo = audit.notes ?: audit.reason
                ))
            }

            events.sortedByDescending { it.timestamp }
        }
    }
}
