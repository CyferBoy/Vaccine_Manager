package com.clinic.neochild.domain.usecase.vaccination

import com.clinic.neochild.data.model.Vaccination
import com.clinic.neochild.domain.repository.VaccinationRepository

class SaveVaccinationUseCase(private val repository: VaccinationRepository) {
    suspend operator fun invoke(vaccination: Vaccination) = repository.addVaccination(vaccination)
}
