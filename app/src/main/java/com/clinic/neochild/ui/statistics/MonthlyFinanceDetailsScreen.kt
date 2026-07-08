package com.clinic.neochild.ui.statistics

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clinic.neochild.ui.components.AppBackground
import com.clinic.neochild.ui.viewmodel.PatientViewModel
import com.clinic.neochild.utils.PatientUtils
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyFinanceDetailsScreen(
    monthKey: String,
    onBack: () -> Unit,
    viewModel: PatientViewModel = viewModel()
) {
    val allPatients by viewModel.allPatients.collectAsState()
    val allVaccinations by viewModel.allVaccinations.collectAsState()

    val monthNames = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    val monthIdx = monthKey.substringAfter("-").toInt()
    val year = monthKey.substringBefore("-")
    val title = "${monthNames[monthIdx]} $year"

    val filteredVaccinations = remember(allVaccinations, monthKey) {
        allVaccinations.filter { v ->
            val cal = Calendar.getInstance().apply { 
                time = PatientUtils.parseDate(v.dateGiven) ?: Date(0)
            }
            val key = String.format(Locale.US, "%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
            key == monthKey
        }.sortedByDescending { PatientUtils.parseDate(it.dateGiven) }
    }

    val totalCash = remember(filteredVaccinations) { filteredVaccinations.sumOf { it.cashAmount } }
    val totalOnline = remember(filteredVaccinations) { filteredVaccinations.sumOf { it.onlineAmount } }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        ) { paddingValues ->
            if (allVaccinations.isEmpty() || allPatients.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (filteredVaccinations.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Text("No records found for this month")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSystemInDarkTheme()) Color(0xFF1B372D) else Color(0xFFE8F5E9),
                                    contentColor = if (isSystemInDarkTheme()) Color(0xFF8FF7BF) else Color(0xFF2E7D32)
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Cash", style = MaterialTheme.typography.labelMedium)
                                    Text("₹${totalCash.toInt()}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                }
                            }
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSystemInDarkTheme()) Color(0xFF003355) else Color(0xFFE3F2FD),
                                    contentColor = if (isSystemInDarkTheme()) Color(0xFFC2E8FF) else Color(0xFF1565C0)
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Online", style = MaterialTheme.typography.labelMedium)
                                    Text("₹${totalOnline.toInt()}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    items(filteredVaccinations) { v ->
                        val patient = allPatients.find { it.id == v.patientId }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = patient?.name ?: "Unknown Patient",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "₹${v.totalPaid.toInt()}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                Text(
                                    text = v.vaccineNames.joinToString(", ") { PatientUtils.cleanVaccineName(it) },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Date: ${PatientUtils.formatDateForDisplay(v.dateGiven)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val mode = when {
                                        v.cashAmount > 0 && v.onlineAmount > 0 -> "Cash + Online"
                                        v.cashAmount > 0 -> "Cash"
                                        v.onlineAmount > 0 -> "Online"
                                        else -> "N/A"
                                    }
                                    Text(
                                        text = "Mode: $mode",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
