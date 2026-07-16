package com.clinic.neochild.ui.statistics

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.clinic.neochild.domain.model.Patient
import com.clinic.neochild.domain.model.Vaccination
import com.clinic.neochild.domain.model.VaccinationSource
import com.clinic.neochild.core.ui.theme.NeoChildTheme
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DueTab(
    patients: List<Patient>, 
    filteredVaccinations: List<Vaccination>,
    overdueCount: Int,
    initialFilter: String = "Today",
    onFilterChanged: (String) -> Unit = {},
    onSearchQueryChanged: (String) -> Unit = {},
    onMarkAsDone: (Vaccination) -> Unit = {},
    onDismissReminder: (Vaccination, String) -> Unit = { _, _ -> },
    onReschedule: (Vaccination, String, String) -> Unit = { _, _, _ -> },
    onVaccinatedElsewhere: (Vaccination, VaccinationSource, String, String) -> Unit = { _, _, _, _ -> }
) {
    var searchQuery by remember { mutableStateOf("") }
    val filters = remember { listOf("Overdue", "Today", "Tomorrow", "This Week", "Upcoming", "Completed", "Dismissed") }
    var selectedVaccination by remember { mutableStateOf<Vaccination?>(null) }
    var showManageSheet by remember { mutableStateOf(false) }
    var showReschedulePicker by remember { mutableStateOf(false) }
    var showElsewhereSheet by remember { mutableStateOf(false) }
    var showDismissDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 80.dp, top = 16.dp)
    ) {
        item {
            Text("Due Vaccination Analytics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    onSearchQueryChanged(it)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search by name, phone or vaccine...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            OverdueSummaryCard(overdueCount = overdueCount)
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            FilterTabRow(
                filters = filters,
                selectedFilter = initialFilter,
                onFilterChanged = onFilterChanged
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (filteredVaccinations.isEmpty()) {
            item {
                EmptyDueState(selectedFilter = initialFilter)
            }
        } else {
            items(
                items = filteredVaccinations, 
                key = { it.patientId + it.nextDueDate + it.nxtVaccineNames.joinToString() }
            ) { v ->
                val patient = remember(v.patientId, patients) { patients.find { it.id == v.patientId } }
                DuePatientCard(
                    vaccination = v, 
                    patient = patient,
                    onLongPress = { 
                        selectedVaccination = v
                        showManageSheet = true 
                    },
                    modifier = Modifier.animateItem()
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    if (showManageSheet && selectedVaccination != null) {
        ManageDueBottomSheet(
            onDismiss = { showManageSheet = false },
            onMarkAsDone = { 
                onMarkAsDone(selectedVaccination!!)
                showManageSheet = false 
            },
            onDismissReminder = {
                showManageSheet = false
                showDismissDialog = true
            },
            onReschedule = { 
                showManageSheet = false
                showReschedulePicker = true 
            },
            onVaccinatedElsewhere = { 
                showManageSheet = false
                showElsewhereSheet = true 
            }
        )
    }

    if (showDismissDialog && selectedVaccination != null) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDismissDialog = false },
            title = { Text("Dismiss Reminder") },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason for dismissal") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    onDismissReminder(selectedVaccination!!, reason)
                    showDismissDialog = false
                }) { Text("Dismiss") }
            },
            dismissButton = {
                TextButton(onClick = { showDismissDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showReschedulePicker && selectedVaccination != null) {
        RescheduleDialog(
            onDismiss = { showReschedulePicker = false },
            onConfirm = { newDate, reason ->
                onReschedule(selectedVaccination!!, newDate, reason)
                showReschedulePicker = false
            }
        )
    }

    if (showElsewhereSheet && selectedVaccination != null) {
        VaccinatedElsewhereBottomSheet(
            onDismiss = { showElsewhereSheet = false },
            onSave = { source, date, notes ->
                onVaccinatedElsewhere(selectedVaccination!!, source, date, notes)
                showElsewhereSheet = false
            }
        )
    }
}

@Composable
private fun OverdueSummaryCard(overdueCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(), 
        colors = CardDefaults.cardColors(
            containerColor = if (overdueCount > 0) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) 
                             else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Error, 
                contentDescription = null, 
                tint = if (overdueCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, 
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("Overdue Vaccinations", fontWeight = FontWeight.Bold, color = if (overdueCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$overdueCount patients currently overdue", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(overdueCount.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = if (overdueCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FilterTabRow(
    filters: List<String>,
    selectedFilter: String,
    onFilterChanged: (String) -> Unit
) {
    ScrollableTabRow(
        selectedTabIndex = filters.indexOf(selectedFilter),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary,
        edgePadding = 0.dp,
        divider = {}
    ) {
        filters.forEach { filter ->
            Tab(
                selected = selectedFilter == filter,
                onClick = { onFilterChanged(filter) },
                text = { Text(filter, style = MaterialTheme.typography.labelLarge) }
            )
        }
    }
}

@Composable
private fun EmptyDueState(selectedFilter: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text("No vaccinations found for $selectedFilter", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DuePatientCard(
    vaccination: Vaccination, 
    patient: Patient?,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { /* Could navigate to details */ },
                onLongClick = onLongPress
            ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(patient?.name ?: "Unknown Patient", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (vaccination.isDone) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = Color(0xFF4CAF50), // Green for DONE
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "DONE",
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Text(
                        text = "Next: ${vaccination.nxtVaccineNames.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (vaccination.performedBy.isNotBlank()) {
                        Text(
                            text = "Added by: ${vaccination.performedBy}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = if (vaccination.isDone) Color.LightGray else MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = vaccination.nextDueDate,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (vaccination.isDone) Color.DarkGray else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    
                    if (patient != null && patient.phone.isNotBlank()) {
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${patient.phone}"))
                            context.startActivity(intent)
                        }) {
                            Icon(Icons.Default.Call, contentDescription = "Call", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageDueBottomSheet(
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
private fun RescheduleDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis()
    )
    
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val date = datePickerState.selectedDateMillis?.let {
                        SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(Date(it))
                    } ?: ""
                    onConfirm(date, reason)
                }
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            DatePicker(state = datePickerState)
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text("Reason (Optional)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaccinatedElsewhereBottomSheet(
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

@Preview(showBackground = true)
@Composable
private fun DueTabPreview() {
    NeoChildTheme {
        DueTab(
            patients = listOf(Patient("1", "John Doe", "1234567890", "", "2020-01-01", "Male", "", "")),
            filteredVaccinations = listOf(Vaccination("1", "1", listOf("BCG"), listOf("HepB"), "1 Jan 2024", "1 Feb 2024", 500.0, 500.0, 0.0, 500.0, false, false, false)),
            overdueCount = 1,
            onMarkAsDone = {},
            onDismissReminder = { _, _ -> },
            onReschedule = { _, _, _ -> },
            onVaccinatedElsewhere = { _, _, _, _ -> }
        )
    }
}
