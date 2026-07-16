package com.clinic.neochild.ui.followup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinic.neochild.data.local.entity.ReminderEntity
import com.clinic.neochild.data.model.Vaccination
import com.clinic.neochild.domain.repository.ReminderRepository
import com.clinic.neochild.domain.usecase.patient.GetPatientsUseCase
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FollowUpUiState(
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class FollowUpViewModel @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val getPatientsUseCase: GetPatientsUseCase,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(FollowUpUiState())
    val uiState: StateFlow<FollowUpUiState> = _uiState.asStateFlow()

    private val currentUserEmail: String
        get() = auth.currentUser?.email ?: "Unknown Staff"

    fun getPatientFollowUps(patientId: String): Flow<List<ReminderEntity>> {
        return reminderRepository.getPatientFollowUps(patientId)
    }

    fun scheduleFollowUp(
        patientId: String,
        originalVisitId: String,
        vaccineNames: List<String>,
        dueDate: String,
        notes: String,
        priority: String,
        reminderEnabled: Boolean,
        onSuccess: () -> Unit
    ) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                reminderRepository.scheduleFollowUp(
                    patientId = patientId,
                    originalVisitId = originalVisitId,
                    vaccineNames = vaccineNames,
                    dueDate = dueDate,
                    notes = notes,
                    priority = priority,
                    reminderEnabled = reminderEnabled,
                    performedBy = currentUserEmail
                )
                _uiState.update { it.copy(isLoading = false, isSaved = true) }
                onSuccess()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun markAsDone(reminder: ReminderEntity) {
        viewModelScope.launch {
            reminderRepository.markAsDone(reminder.toPendingRequirement(), currentUserEmail)
        }
    }

    fun reschedule(reminder: ReminderEntity, newDate: String, reason: String) {
        viewModelScope.launch {
            reminderRepository.reschedule(reminder.toPendingRequirement(), newDate, reason, currentUserEmail)
        }
    }

    fun markVaccinatedElsewhere(reminder: ReminderEntity, source: com.clinic.neochild.data.model.VaccinationSource, date: String, notes: String) {
        viewModelScope.launch {
            reminderRepository.markVaccinatedElsewhere(reminder.toPendingRequirement(), source, date, notes, currentUserEmail)
        }
    }

    fun dismissReminder(reminder: ReminderEntity, reason: String) {
        viewModelScope.launch {
            reminderRepository.dismissReminder(reminder.toPendingRequirement(), reason, currentUserEmail)
        }
    }

    fun restoreReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            reminderRepository.restoreReminder(reminder.toPendingRequirement(), currentUserEmail)
        }
    }

    fun deleteReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            reminderRepository.deleteReminder(reminder.toPendingRequirement(), currentUserEmail)
        }
    }

    private fun ReminderEntity.toPendingRequirement() = com.clinic.neochild.utils.PendingRequirement(
        patientId = patientId,
        vaccineName = vaccineName,
        dueDate = com.clinic.neochild.utils.PatientUtils.parseDate(dueDate) ?: java.util.Date(),
        originalVisitId = originalVisitId
    )
}
