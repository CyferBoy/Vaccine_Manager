package com.clinic.neochild.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinic.neochild.data.local.entity.SyncQueueEntity
import com.clinic.neochild.domain.repository.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SyncUiState(
    val queue: List<SyncQueueEntity> = emptyList(),
    val isProcessing: Boolean = false,
    val lastSyncTime: Long = 0
)

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val syncRepository: SyncRepository
) : ViewModel() {

    val uiState: StateFlow<SyncUiState> = syncRepository.getSyncQueue()
        .map { list ->
            SyncUiState(
                queue = list,
                lastSyncTime = list.filter { it.status == "SYNCED" }.maxOfOrNull { it.updatedAt } ?: 0L
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SyncUiState()
        )

    fun syncNow() {
        viewModelScope.launch {
            syncRepository.processNextItems()
        }
    }

    fun retryFailed() {
        viewModelScope.launch {
            syncRepository.retryFailedItems()
            syncRepository.processNextItems()
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            syncRepository.clearSyncedItems()
        }
    }
}
