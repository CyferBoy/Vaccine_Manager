package com.clinic.neochild.ui.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clinic.neochild.data.model.Patient
import com.clinic.neochild.data.model.Vaccination

@Composable
fun OverviewTab(patients: List<Patient>, vaccinations: List<Vaccination>) {
    val availableYears = remember(patients, vaccinations) {
        StatisticsUtils.getAvailableFinancialYears(patients.map { it.registrationDate } + vaccinations.map { it.dateGiven })
    }

    var filterMode by remember { mutableStateOf("Overall") }
    val mainOptions = listOf("Overall") + availableYears.map { "FY $it" }
    
    var fyQuarter by remember { mutableIntStateOf(0) }
    var selectedMonth by remember { mutableIntStateOf(-1) }

    val filteredPatients = patients.filter { StatisticsUtils.isDateInFilter(it.registrationDate, filterMode, fyQuarter, selectedMonth) }
    val filteredVaccinations = vaccinations.filter { StatisticsUtils.isDateInFilter(it.dateGiven, filterMode, fyQuarter, selectedMonth) }

    val stats = listOf(
        StatItem("New Patients", filteredPatients.size.toString(), Icons.Default.PersonAdd, Color(0xFF4CAF50)),
        StatItem("Active Patients", filteredVaccinations.map { it.patientId }.distinct().size.toString(), Icons.Default.PersonSearch, Color(0xFF673AB7)),
        StatItem("Vaccinations", filteredVaccinations.sumOf { it.vaccineNames.size }.toString(), Icons.Default.Vaccines, Color(0xFFFF9800)),
        StatItem("Revenue", "₹${filteredVaccinations.sumOf { it.totalPaid }.toInt()}", Icons.Default.Payments, Color(0xFF3F51B5)),
        StatItem("Cash", "₹${filteredVaccinations.sumOf { it.cashAmount }.toInt()}", Icons.Default.Money, Color(0xFF4CAF50)),
        StatItem("Online", "₹${filteredVaccinations.sumOf { it.onlineAmount }.toInt()}", Icons.Default.AccountBalanceWallet, Color(0xFF03A9F4))
    )

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            val subtitle = when {
                filterMode == "Overall" -> "All Time"
                selectedMonth != -1 -> "${StatisticsUtils.monthNames[selectedMonth]} Statistics"
                fyQuarter != 0 -> "Quarter ${fyQuarter} Statistics"
                else -> "Annual Statistics ($filterMode)"
            }
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Overview", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                
                var expanded by remember { mutableStateOf(false) }
                Box {
                    AssistChip(
                        onClick = { expanded = true },
                        label = { Text(filterMode) },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        mainOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    filterMode = option
                                    fyQuarter = 0
                                    selectedMonth = -1
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        if (filterMode.startsWith("FY ")) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Text("Quarters", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatisticsUtils.fyQuarters.forEachIndexed { index, _ ->
                            val qNum = index + 1
                            FilterChip(
                                selected = fyQuarter == qNum,
                                onClick = { 
                                    fyQuarter = if (fyQuarter == qNum) 0 else qNum 
                                    selectedMonth = -1
                                },
                                label = { Text("Q$qNum") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    
                    if (fyQuarter > 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Months in Q$fyQuarter", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            StatisticsUtils.fyQuarters[fyQuarter - 1].second.forEach { mIdx ->
                                FilterChip(
                                    selected = selectedMonth == mIdx,
                                    onClick = { 
                                        selectedMonth = if (selectedMonth == mIdx) -1 else mIdx 
                                    },
                                    label = { Text(StatisticsUtils.monthNames[mIdx], style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (i in stats.indices step 2) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        StatCardSmall(Modifier.weight(1f), stats[i].title, stats[i].value, stats[i].icon, stats[i].color)
                        Spacer(modifier = Modifier.width(12.dp))
                        if (i + 1 < stats.size) {
                            StatCardSmall(Modifier.weight(1f), stats[i + 1].title, stats[i + 1].value, stats[i + 1].icon, stats[i + 1].color)
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}
