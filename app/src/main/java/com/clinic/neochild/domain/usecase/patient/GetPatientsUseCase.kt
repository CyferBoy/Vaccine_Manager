package com.clinic.neochild.domain.usecase.patient

import com.clinic.neochild.domain.model.Patient
import com.clinic.neochild.domain.repository.PatientRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case to retrieve all patients.
 * Provides a clean entry point for the UI to observe patient data.
 */
class GetPatientsUseCase @Inject constructor(private val repository: PatientRepository) {
    operator fun invoke(): Flow<List<Patient>> = repository.allPatients
}
