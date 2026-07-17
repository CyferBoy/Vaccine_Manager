package com.clinic.neochild.features.reminder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.clinic.neochild.domain.model.VaccinationSource
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageDueBottomSheet(
    onDismiss: () -> Unit,
    onMarkAsDone: () -> Unit,
    onDismissReminder: () -> Unit,
    onReschedule: () -> Unit,
    onVaccinatedElsewhere: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Manage Due Vaccination",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp)
            )
            
            ListItem(
                headlineContent = { Text("Mark as Done") },
                supportingContent = { Text("Given in this clinic today") },
                leadingContent = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50)) },
                modifier = Modifier.clickable { onMarkAsDone() }
            )
            ListItem(
                headlineContent = { Text("Dismiss Reminder") },
                supportingContent = { Text("Stop reminders for this vaccine") },
                leadingContent = { Icon(Icons.Default.NotificationsOff, contentDescription = null) },
                modifier = Modifier.clickable { onDismissReminder() }
            )
            ListItem(
                headlineContent = { Text("Reschedule") },
                supportingContent = { Text("Change the due date") },
                leadingContent = { Icon(Icons.Default.Event, contentDescription = null) },
                modifier = Modifier.clickable { onReschedule() }
            )
            ListItem(
                headlineContent = { Text("Vaccinated Elsewhere") },
                supportingContent = { Text("Recorded at another facility") },
                leadingContent = { Icon(Icons.Default.Public, contentDescription = null) },
                modifier = Modifier.clickable { onVaccinatedElsewhere() }
            )
            ListItem(
                headlineContent = { Text("Cancel") },
                leadingContent = { Icon(Icons.Default.Close, contentDescription = null) },
                modifier = Modifier.clickable { onDismiss() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaccinatedElsewhereBottomSheet(
    onDismiss: () -> Unit,
    onSave: (VaccinationSource, String, String) -> Unit
) {
    var source by remember { mutableStateOf(VaccinationSource.GOVERNMENT) }
    var notes by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(Date())) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            selectedDate = SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(Date(it))
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Vaccinated Elsewhere",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Where was the vaccine given?", style = MaterialTheme.typography.titleMedium)
            
            VaccinationSource.entries.filter { it != VaccinationSource.CLINIC }.forEach { entry ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { source = entry }
                ) {
                    RadioButton(selected = source == entry, onClick = { source = entry })
                    Text(
                        text = entry.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CalendarToday, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Vaccination Date: $selectedDate")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Optional Notes") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = { onSave(source, selectedDate, notes) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
        }
    }
}
