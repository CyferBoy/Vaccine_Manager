package com.clinic.neochild.features.audit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinic.neochild.data.local.dao.AuditLogDao
import com.clinic.neochild.data.local.entity.AuditLogEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class AuditLogUiState(
    val logs: List<AuditLogEntity> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class FullAuditLogViewModel @Inject constructor(
    private val auditLogDao: AuditLogDao
) : ViewModel() {

    val uiState: StateFlow<AuditLogUiState> = auditLogDao.getAllLogs()
        .map { AuditLogUiState(logs = it, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AuditLogUiState()
        )
}
