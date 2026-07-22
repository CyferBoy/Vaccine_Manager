package com.clinic.neochild.domain.usecase.patient

import com.clinic.neochild.domain.model.Patient
import com.clinic.neochild.domain.model.Vaccination
import com.clinic.neochild.domain.model.TimelineEvent
import com.clinic.neochild.domain.model.TimelineEventType
import com.clinic.neochild.domain.repository.PatientRepository
import com.clinic.neochild.core.utils.PatientUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetPatientTimelineUseCase @Inject constructor(
    private val patientRepository: PatientRepository
) {
    operator fun invoke(
        patient: Patient,
        vaccinations: List<Vaccination>
    ): Flow<List<TimelineEvent>> {
        return patientRepository.getPatientTimeline(patient.id).map { logs ->
            logs.map { log ->
                TimelineEvent(
                    id = log.id.toString(),
                    type = when (log.action) {
                        "CREATED" -> TimelineEventType.REGISTRATION
                        "VACCINATION", "VISIT" -> TimelineEventType.VACCINATION
                        "EXTERNAL" -> TimelineEventType.EXTERNAL_VACCINATION
                        else -> TimelineEventType.AUDIT_LOG
                    },
                    title = log.action.lowercase().replaceFirstChar { it.uppercase() },
                    subtitle = log.remarks ?: "${log.entityType} ${log.entityId}",
                    timestamp = log.timestamp,
                    dateDisplay = PatientUtils.formatDate(java.util.Date(log.timestamp)),
                    performedBy = log.user,
                    extraInfo = log.newValue
                )
            }.sortedByDescending { it.timestamp }
        }
    }
}
