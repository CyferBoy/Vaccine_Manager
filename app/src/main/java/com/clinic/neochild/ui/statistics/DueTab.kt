package com.clinic.neochild.ui.statistics

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.clinic.neochild.data.model.Patient
import com.clinic.neochild.data.model.Vaccination
import com.clinic.neochild.ui.theme.NeoChildTheme
import com.clinic.neochild.utils.PatientUtils
import com.clinic.neochild.utils.ReminderEngine
import java.util.*

@Composable
fun DueTab(
    patients: List<Patient>, 
    vaccinations: List<Vaccination>,
    initialFilter: String = "Today",
    onFilterChanged: (String) -> Unit = {},
    onUpdateVaccination: (Vaccination) -> Unit = {}
) {
    val filters = remember { listOf("Overdue", "Previous Month", "Today", "This Week", "Upcoming") }

    val unsatisfied = remember(vaccinations) { ReminderEngine.getUnsatisfiedRequirements(vaccinations) }
    val pendingVaccinations = remember(unsatisfied, vaccinations) {
        unsatisfied.mapNotNull { req ->
            vaccinations.find { it.id == req.originalVisitId }?.copy(
                nxtVaccineNames = listOf(req.vaccineName),
                nextDueDate = PatientUtils.formatDate(req.dueDate)
            )
        }.distinctBy { it.patientId + it.nextDueDate + it.nxtVaccineNames.joinToString() }
    }

    val filteredVaccinations = remember(pendingVaccinations, initialFilter) { 
        PatientUtils.filterVaccinationsByPeriod(pendingVaccinations, initialFilter) 
    }

    val overdueCount = remember(pendingVaccinations) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val todayStart = calendar.time
        pendingVaccinations.count { 
            val date = PatientUtils.parseDate(it.nextDueDate)
            date != null && date.before(todayStart)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 80.dp, top = 16.dp)
    ) {
        item {
            Text("Due Vaccination Analytics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
            items(filteredVaccinations, key = { it.id }) { v ->
                val patient = remember(v.patientId, patients) { patients.find { it.id == v.patientId } }
                DuePatientCard(
                    vaccination = v, 
                    patient = patient,
                    onStatusChange = { isDone -> onUpdateVaccination(v.copy(isDone = isDone)) },
                    modifier = Modifier.animateItem()
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
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
    onStatusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { /* Could navigate to details */ },
                onLongClick = { showMenu = true }
            ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(patient?.name ?: "Unknown Patient", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Next: ${vaccination.nxtVaccineNames.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = vaccination.nextDueDate,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
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

            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Mark as Done") },
                    onClick = {
                        onStatusChange(true)
                        showMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) }
                )
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
            vaccinations = listOf(Vaccination("1", "1", listOf("BCG"), listOf("HepB"), "1 Jan 2024", "1 Feb 2024", 500.0, 500.0, 0.0, 500.0, false, false, false))
        )
    }
}
