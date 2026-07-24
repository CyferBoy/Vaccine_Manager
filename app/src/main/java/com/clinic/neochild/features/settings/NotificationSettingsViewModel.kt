package com.clinic.neochild.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinic.neochild.features.settings.NotificationSettings
import com.clinic.neochild.features.settings.NotificationSettingsManager
import com.clinic.neochild.core.constants.Constants
import com.clinic.neochild.domain.usecase.inventory.BackfillInventoryUsageUseCase
import com.clinic.neochild.domain.usecase.inventory.BackfillResult
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val settingsManager: NotificationSettingsManager,
    private val backfillInventoryUsageUseCase: BackfillInventoryUsageUseCase,
    private val auth: FirebaseAuth
) : ViewModel() {

    val settings: StateFlow<NotificationSettings?> = settingsManager.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _isBackfilling = MutableStateFlow(false)
    val isBackfilling = _isBackfilling.asStateFlow()

    private val _backfillResults = MutableStateFlow<List<BackfillResult>?>(null)
    val backfillResults = _backfillResults.asStateFlow()

    val isAdmin: Boolean
        get() = Constants.ADMIN_EMAILS.contains(auth.currentUser?.email)

    fun updateSettings(settings: NotificationSettings) {
        viewModelScope.launch {
            settingsManager.updateSettings(settings)
        }
    }

    fun runInventoryBackfill() {
        if (!isAdmin) return
        
        viewModelScope.launch {
            _isBackfilling.value = true
            _backfillResults.value = null
            val user = auth.currentUser?.email ?: "Unknown Admin"
            val results = backfillInventoryUsageUseCase.execute(user)
            _backfillResults.value = results
            _isBackfilling.value = false
        }
    }

    fun clearBackfillResults() {
        _backfillResults.value = null
    }
}
