package com.clinic.neochild.ui.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clinic.neochild.data.model.Vaccination
import com.clinic.neochild.utils.PatientUtils
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaccinationsTab(vaccinations: List<Vaccination>) {
    val availableYears = remember(vaccinations) {
        StatisticsUtils.getAvailableFinancialYears(vaccinations.map { it.dateGiven })
    }

    var filterMode by remember { mutableStateOf("Overall") }
    val mainOptions = listOf("Overall") + availableYears.reversed().map { "FY $it" }
    
    var fyQuarter by remember { mutableIntStateOf(0) }
    var selectedMonth by remember { mutableIntStateOf(-1) }

    val filteredVaccinations = vaccinations.filter { StatisticsUtils.isDateInFilter(it.dateGiven, filterMode, fyQuarter, selectedMonth) }

    val vaccineCounts = mutableMapOf<String, Int>()
    filteredVaccinations.forEach { v ->
        v.vaccineNames.forEach { name ->
            val cleanName = PatientUtils.cleanVaccineName(name)
            vaccineCounts[cleanName] = (vaccineCounts[cleanName] ?: 0) + 1
        }
    }

    val sortedVaccines = vaccineCounts.toList().sortedByDescending { it.second }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
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
                Text("Vaccination Analytics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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

        if (filterMode.startsWith("FY ")) {
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

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            StatCardSmall(Modifier.weight(1f), "Total Doses", filteredVaccinations.sumOf { it.vaccineNames.size }.toString(), Icons.Default.FactCheck, Color(0xFF4CAF50))
            Spacer(modifier = Modifier.width(12.dp))
            StatCardSmall(Modifier.weight(1f), "Avg Per Day", String.format(Locale.getDefault(), "%.1f", if (filteredVaccinations.isEmpty()) 0.0 else filteredVaccinations.size.toDouble() / 30), Icons.Default.Timeline, Color(0xFF2196F3))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("All Administered Vaccines", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))

        sortedVaccines.forEach { (name, count) ->
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(name, style = MaterialTheme.typography.bodyMedium)
                    Text("$count", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                val maxCount = sortedVaccines.firstOrNull()?.second ?: 1
                LinearProgressIndicator(
                    progress = { count.toFloat() / maxCount },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
