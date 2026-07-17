package com.clinic.neochild.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinic.neochild.domain.repository.PatientRepository
import com.clinic.neochild.domain.repository.WasteRepository
import com.clinic.neochild.domain.usecase.statistics.GetClinicStatsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class DashboardUiState(
    val patientCount: Int = 0,
    val lowStockCount: Int = 0,
    val dueTodayCount: Int = 0,
    val wasteCount: Int = 0
)

/**
 * Orchestrates Dashboard data using unified data streams.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val patientRepository: PatientRepository,
    private val getClinicStatsUseCase: GetClinicStatsUseCase,
    private val wasteRepository: WasteRepository
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        patientRepository.getPatientCount(),
        getClinicStatsUseCase(),
        wasteRepository.getWasteCount()
    ) { patientCount, stats, wasteCount ->
        DashboardUiState(
            patientCount = patientCount,
            lowStockCount = stats.lowStockCount,
            dueTodayCount = stats.dueToday,
            wasteCount = wasteCount
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())
}
