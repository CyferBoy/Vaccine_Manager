package com.clinic.neochild.feature_patient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinic.neochild.domain.model.Patient
import com.clinic.neochild.domain.usecase.patient.DeletePatientUseCase
import com.clinic.neochild.domain.usecase.patient.GetPatientsUseCase
import com.clinic.neochild.domain.usecase.patient.MergePatientsUseCase
import com.clinic.neochild.domain.usecase.patient.SearchPatientsUseCase
import com.clinic.neochild.domain.usecase.vaccination.GetVaccinationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PatientSortOption {
    NAME_AZ,
    NEWEST
}

data class PatientListUiState(
    val patients: List<Patient> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val sortOption: PatientSortOption = PatientSortOption.NAME_AZ,
    val isMergeSelectionMode: Boolean = false,
    val selectedPatients: Set<Patient> = emptySet(),
    val isMerging: Boolean = false,
    val patientsWithMissingPrice: Set<String> = emptySet()
)

@HiltViewModel
class PatientListViewModel @Inject constructor(
    private val getPatientsUseCase: GetPatientsUseCase,
    private val getVaccinationsUseCase: GetVaccinationsUseCase,
    private val deletePatientUseCase: DeletePatientUseCase,
    private val mergePatientsUseCase: MergePatientsUseCase,
    private val searchPatientsUseCase: SearchPatientsUseCase
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _sortOption = MutableStateFlow(PatientSortOption.NAME_AZ)
    private val _isMergeSelectionMode = MutableStateFlow(false)
    private val _selectedPatients = MutableStateFlow<Set<Patient>>(emptySet())
    private val _isMerging = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PatientListUiState> = combine(
        getPatientsUseCase(),
        getVaccinationsUseCase(),
        _searchQuery,
        _sortOption,
        combine(_isMergeSelectionMode, _selectedPatients, _isMerging, _error) { mode, selected, merging, err ->
            Quad(mode, selected, merging, err)
        }
    ) { patients, vaccinations, query, sort, internalState ->
        
        val missingPrice = vaccinations.filter { it.cost <= 0.0 }.map { it.patientId }.toSet()
        
        val filtered = searchPatientsUseCase(query, patients, vaccinations).let { list ->
            when (sort) {
                PatientSortOption.NAME_AZ -> list.sortedBy { it.name.lowercase() }
                PatientSortOption.NEWEST -> list.sortedByDescending { it.registrationDate }
            }
        }

        PatientListUiState(
            patients = filtered,
            isLoading = false,
            searchQuery = query,
            sortOption = sort,
            isMergeSelectionMode = internalState.first,
            selectedPatients = internalState.second,
            isMerging = internalState.third,
            error = internalState.fourth,
            patientsWithMissingPrice = missingPrice
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PatientListUiState(isLoading = true))

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateSortOption(option: PatientSortOption) {
        _sortOption.value = option
    }

    fun toggleSelection(patient: Patient) {
        val currentSelected = _selectedPatients.value
        val newSelected = if (currentSelected.contains(patient)) {
            currentSelected - patient
        } else {
            currentSelected + patient
        }
        _selectedPatients.value = newSelected
        _isMergeSelectionMode.value = newSelected.isNotEmpty()
    }

    fun clearSelection() {
        _selectedPatients.value = emptySet()
        _isMergeSelectionMode.value = false
    }

    fun enterMergeMode(initialPatient: Patient) {
        _selectedPatients.value = setOf(initialPatient)
        _isMergeSelectionMode.value = true
    }

    fun deletePatient(id: String) {
        viewModelScope.launch {
            deletePatientUseCase(id)
        }
    }

    fun mergeSelectedPatients(master: Patient) {
        val selected = _selectedPatients.value
        val secondary = selected.find { it != master }
        if (secondary != null) {
            viewModelScope.launch {
                _isMerging.value = true
                try {
                    mergePatientsUseCase(master.id, listOf(secondary.id))
                    clearSelection()
                } catch (e: Exception) {
                    _error.value = e.message
                } finally {
                    _isMerging.value = false
                }
            }
        }
    }

    fun autoMergeDuplicates() {
        viewModelScope.launch {
            _isMerging.value = true
            try {
                val patientsList = uiState.value.patients
                val groups = patientsList.groupBy { 
                    it.name.trim().lowercase() + "|" + it.phone.trim()
                }.filter { it.value.size > 1 }

                for (group in groups.values) {
                    val master = group[0]
                    val duplicates = group.drop(1).map { it.id }
                    mergePatientsUseCase(master.id, duplicates)
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isMerging.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}

data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
