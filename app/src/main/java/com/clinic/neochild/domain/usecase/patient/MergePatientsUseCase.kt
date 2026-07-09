package com.clinic.neochild.domain.usecase.patient

import com.clinic.neochild.domain.repository.PatientRepository
import javax.inject.Inject

class MergePatientsUseCase @Inject constructor(
    private val repository: PatientRepository
) {
    suspend operator fun invoke(masterId: String, duplicateIds: List<String>) {
        if (masterId.isBlank() || duplicateIds.isEmpty()) return
        repository.mergePatients(masterId, duplicateIds)
    }
}
