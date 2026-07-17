package com.clinic.neochild.features.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinic.neochild.domain.model.Patient
import com.clinic.neochild.domain.model.Vaccination
import com.clinic.neochild.domain.usecase.patient.GetPatientsUseCase
import com.clinic.neochild.domain.usecase.vaccination.GetVaccinationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class StatisticsUiState(
    val patients: List<Patient> = emptyList(),
    val vaccinations: List<Vaccination> = emptyList(),
    val isLoading: Boolean = false,
    val selectedTab: Int = 0
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val getPatientsUseCase: GetPatientsUseCase,
    private val getVaccinationsUseCase: GetVaccinationsUseCase
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab = _selectedTab.asStateFlow()

    val uiState: StateFlow<StatisticsUiState> = combine(
        getPatientsUseCase(),
        getVaccinationsUseCase(),
        _selectedTab
    ) { patients, vaccinations, tab ->
        StatisticsUiState(patients, vaccinations, false, tab)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatisticsUiState(isLoading = true))

    fun updateTab(tab: Int) {
        _selectedTab.value = tab
    }
}
