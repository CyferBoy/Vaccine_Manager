package com.clinic.neochild.domain.usecase.statistics

import com.clinic.neochild.domain.manager.ClinicStatsManager
import com.clinic.neochild.domain.model.ClinicStats
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Calculates high-level clinic performance metrics.
 * 
 * Improvement: Delegates business logic and repository coordination to [ClinicStatsManager]
 * to adhere to the production standard: UI -> ViewModel -> UseCase -> Manager -> Repository.
 */
class GetClinicStatsUseCase @Inject constructor(
    private val statsManager: ClinicStatsManager
) {
    operator fun invoke(): Flow<ClinicStats> = statsManager.getClinicStats()
}
