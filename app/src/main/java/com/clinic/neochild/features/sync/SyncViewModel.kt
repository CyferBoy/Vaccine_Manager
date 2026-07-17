package com.clinic.neochild.features.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinic.neochild.domain.repository.SyncRepository
import com.clinic.neochild.core.model.SyncItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val syncRepository: SyncRepository
) : ViewModel() {

    val syncQueue: StateFlow<List<SyncItem>> = syncRepository.getSyncQueue()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun processSync() {
        viewModelScope.launch {
            syncRepository.processNextItems()
        }
    }

    fun retryFailed() {
        viewModelScope.launch {
            syncRepository.retryFailedItems()
        }
    }

    fun clearSynced() {
        viewModelScope.launch {
            syncRepository.clearSyncedItems()
        }
    }
}
