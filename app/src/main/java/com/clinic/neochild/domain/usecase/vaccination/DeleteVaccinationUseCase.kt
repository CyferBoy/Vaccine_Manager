package com.clinic.neochild.domain.usecase.vaccination

import com.clinic.neochild.domain.repository.VaccinationRepository

class DeleteVaccinationUseCase(private val repository: VaccinationRepository) {
    suspend operator fun invoke(id: String) = repository.deleteVaccination(id)
}
