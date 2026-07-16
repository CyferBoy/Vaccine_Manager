package com.clinic.neochild.domain.usecase.patient

import com.clinic.neochild.domain.model.Patient
import com.clinic.neochild.domain.repository.PatientRepository
import javax.inject.Inject

class GetPatientByIdUseCase @Inject constructor(private val repository: PatientRepository) {
    suspend operator fun invoke(id: String): Patient? = repository.getPatientById(id)
}
