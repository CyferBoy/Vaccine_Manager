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
import com.clinic.neochild.core.ui.components.AppBackground

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
                    Text("General Alerts", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    
                    SettingSwitch(
                        label = "Daily Summary Notification",
                        supportingText = "Sends one notification at 08:00 AM with today's tasks",
                        checked = settings.dailySummaryEnabled,
                        onCheckedChange = { viewModel.updateSettings(settings.copy(dailySummaryEnabled = it)) }
                    )

                    SettingItem(label = "Summary Time", value = settings.reminderTime) {
                        // Time picker implementation
                    }

                    HorizontalDivider()

                    Text("Inventory & System", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

                    SettingSwitch(
                        label = "Low Stock Alerts",
                        supportingText = "Notify when vaccine stock is low",
                        checked = settings.lowStockEnabled,
                        onCheckedChange = { viewModel.updateSettings(settings.copy(lowStockEnabled = it)) }
                    )

                    SettingSlider(
                        label = "Low Stock Threshold",
                        value = settings.lowStockThreshold.toFloat(),
                        range = 1f..20f,
                        steps = 19,
                        onValueChange = { viewModel.updateSettings(settings.copy(lowStockThreshold = it.toInt())) }
                    )

                    SettingSwitch(
                        label = "Sync & Backup Alerts",
                        supportingText = "High priority alerts for data failures",
                        checked = settings.syncAlertsEnabled,
                        onCheckedChange = { viewModel.updateSettings(settings.copy(syncAlertsEnabled = it)) }
                    )
                }
            } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun SettingSwitch(label: String, supportingText: String? = null, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (supportingText != null) {
                Text(supportingText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
