package com.clinic.neochild.domain.usecase.patient

import com.clinic.neochild.domain.repository.PatientRepository

class DeletePatientUseCase(private val repository: PatientRepository) {
    suspend operator fun invoke(id: String) = repository.deletePatient(id)
}
