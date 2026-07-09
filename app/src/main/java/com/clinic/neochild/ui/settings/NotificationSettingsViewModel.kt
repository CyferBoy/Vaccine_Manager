package com.clinic.neochild.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinic.neochild.data.local.preferences.NotificationSettings
import com.clinic.neochild.data.local.preferences.NotificationSettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val settingsManager: NotificationSettingsManager
) : ViewModel() {

    val settings: StateFlow<NotificationSettings?> = settingsManager.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun updateSettings(settings: NotificationSettings) {
        viewModelScope.launch {
            settingsManager.updateSettings(settings)
        }
    }
}
