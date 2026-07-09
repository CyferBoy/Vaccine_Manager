package com.clinic.neochild.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.clinic.neochild.ui.components.AppBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    viewModel: NotificationSettingsViewModel = hiltViewModel()
) {
    val settingsState by viewModel.settings.collectAsState()

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Notification Settings") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { paddingValues ->
            settingsState?.let { settings ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SettingSwitch(
                        label = "Enable Notifications",
                        checked = settings.enabled,
                        onCheckedChange = { viewModel.updateSettings(settings.copy(enabled = it)) }
                    )

                    HorizontalDivider()

                    SettingItem(label = "Reminder Time", value = settings.reminderTime) {
                        // In a real app, show TimePickerDialog
                    }

                    SettingSlider(
                        label = "Reminder Days Before",
                        value = settings.reminderDaysBefore.toFloat(),
                        range = 1f..7f,
                        steps = 6,
                        onValueChange = { viewModel.updateSettings(settings.copy(reminderDaysBefore = it.toInt())) }
                    )

                    SettingSlider(
                        label = "Overdue Reminder Frequency (Days)",
                        value = settings.overdueFrequencyDays.toFloat(),
                        range = 1f..15f,
                        steps = 14,
                        onValueChange = { viewModel.updateSettings(settings.copy(overdueFrequencyDays = it.toInt())) }
                    )

                    SettingSlider(
                        label = "Low Stock Threshold",
                        value = settings.lowStockThreshold.toFloat(),
                        range = 1f..20f,
                        steps = 19,
                        onValueChange = { viewModel.updateSettings(settings.copy(lowStockThreshold = it.toInt())) }
                    )

                    SettingSlider(
                        label = "Expiry Reminder Days",
                        value = settings.expiryDaysBefore.toFloat(),
                        range = 7f..90f,
                        steps = 83,
                        onValueChange = { viewModel.updateSettings(settings.copy(expiryDaysBefore = it.toInt())) }
                    )

                    SettingSwitch(
                        label = "Enable SMS Reminders",
                        checked = settings.smsEnabled,
                        onCheckedChange = { viewModel.updateSettings(settings.copy(smsEnabled = it)) }
                    )
                }
            } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun SettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingItem(label: String, value: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun SettingSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(value.toInt().toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps
        )
    }
}
