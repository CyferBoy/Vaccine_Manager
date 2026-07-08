package com.clinic.neochild.ui.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.clinic.neochild.data.model.Vaccination
import com.clinic.neochild.data.model.Vaccine
import com.clinic.neochild.utils.FirestoreMappers
import com.clinic.neochild.utils.PatientUtils
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

data class FinanceSummaryItem(val label: String, val revenue: Double, val profit: Double, val key: String)

@Composable
fun FinanceTab(vaccinations: List<Vaccination>, onMonthClick: (String) -> Unit = {}) {
    var inventory by remember { mutableStateOf<List<Vaccine>>(emptyList()) }
    
    LaunchedEffect(Unit) {
        FirebaseFirestore.getInstance().collection("inventory").get().addOnSuccessListener { result ->
            inventory = result.documents.mapNotNull { FirestoreMappers.toVaccine(it) }
        }
    }

    val availableYears = remember(vaccinations) {
        StatisticsUtils.getAvailableFinancialYears(vaccinations.map { it.dateGiven })
    }

    var filterMode by remember { mutableStateOf("Overall") }
    val mainOptions = listOf("Overall") + availableYears.map { "FY $it" }

    val filteredVaccinations = vaccinations.filter { StatisticsUtils.isDateInFilter(it.dateGiven, filterMode) }

    val totalRevenue = filteredVaccinations.sumOf { it.totalPaid }
    val cashTotal = filteredVaccinations.sumOf { it.cashAmount }
    val onlineTotal = filteredVaccinations.sumOf { it.onlineAmount }
    
    val totalNetRate = filteredVaccinations.sumOf { v ->
        v.vaccineNames.sumOf { name ->
            inventory.find { it.brandName == name }?.netRate ?: 0.0
        }
    }
    val totalProfit = totalRevenue - totalNetRate

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Financial Analytics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            
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
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(if (filterMode == "Overall") "Overall Revenue" else "Revenue ($filterMode)", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                Text("₹${String.format(Locale.getDefault(), "%,.0f", totalRevenue)}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            FinanceStatItem(Modifier.weight(1f), "Cash Total", "₹${cashTotal.toInt()}", Color(0xFF4CAF50))
            Spacer(modifier = Modifier.width(12.dp))
            FinanceStatItem(Modifier.weight(1f), "Online Total", "₹${onlineTotal.toInt()}", Color(0xFF2196F3))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Finance Table
        Text("Financial Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        FinanceTable(filteredVaccinations, inventory, onMonthClick)

        Spacer(modifier = Modifier.height(32.dp))

        if (filterMode == "Overall") {
            // --- TOTAL REVENUE TREND (YEARLY) ---
            Text("Total Revenue Trend (Yearly)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            
            val yearlyLabels = availableYears
            val yearlyTotals = availableYears.map { fy ->
                val revenue = vaccinations.filter { 
                    StatisticsUtils.isDateInFilter(it.dateGiven, "FY $fy")
                }.sumOf { it.totalPaid }.toFloat()
                listOf(revenue)
            }
            MultiBarChart(yearlyLabels, yearlyTotals, colors = listOf(MaterialTheme.colorScheme.primary))

            Spacer(modifier = Modifier.height(32.dp))

            // --- BREAKDOWN TREND (YEARLY) ---
            Text("Cash vs Online Breakdown (Yearly)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            
            val yearlyBreakdown = availableYears.map { fy ->
                val fyVax = vaccinations.filter { 
                    StatisticsUtils.isDateInFilter(it.dateGiven, "FY $fy")
                }
                val cash = fyVax.sumOf { it.cashAmount }.toFloat()
                val online = fyVax.sumOf { it.onlineAmount }.toFloat()
                listOf(cash, online)
            }
            MultiBarChart(yearlyLabels, yearlyBreakdown, colors = listOf(Color(0xFF4CAF50), Color(0xFF2196F3)))
            
        } else {
            // --- TOTAL REVENUE TREND (MONTHLY) ---
            Text("Total Revenue Trend (Monthly)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            
            val startYearShort = filterMode.substringAfter("FY ").substringBefore("-").toInt()
            val fyStartYear = if (startYearShort > 80) 1900 + startYearShort else 2000 + startYearShort
            
            val monthLabels = listOf("Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec", "Jan", "Feb", "Mar")
            
            val monthlyTotals = (4..15).map { mAdjusted ->
                val monthIdx = (mAdjusted - 1) % 12
                val yearAdjusted = if (mAdjusted > 12) fyStartYear + 1 else fyStartYear
                val monthlyVax = vaccinations.filter {
                    val date = PatientUtils.parseDate(it.dateGiven) ?: return@filter false
                    val cal = Calendar.getInstance().apply { time = date }
                    cal.get(Calendar.MONTH) == monthIdx && cal.get(Calendar.YEAR) == yearAdjusted
                }
                listOf(monthlyVax.sumOf { it.totalPaid }.toFloat())
            }
            MultiBarChart(monthLabels, monthlyTotals, colors = listOf(MaterialTheme.colorScheme.primary))

            Spacer(modifier = Modifier.height(32.dp))

            // --- BREAKDOWN TREND (MONTHLY) ---
            Text("Cash vs Online Breakdown (Monthly)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            
            val monthlyBreakdown = (4..15).map { mAdjusted ->
                val monthIdx = (mAdjusted - 1) % 12
                val yearAdjusted = if (mAdjusted > 12) fyStartYear + 1 else fyStartYear
                val monthlyVax = vaccinations.filter {
                    val date = PatientUtils.parseDate(it.dateGiven) ?: return@filter false
                    val cal = Calendar.getInstance().apply { time = date }
                    cal.get(Calendar.MONTH) == monthIdx && cal.get(Calendar.YEAR) == yearAdjusted
                }
                val cash = monthlyVax.sumOf { it.cashAmount }.toFloat()
                val online = monthlyVax.sumOf { it.onlineAmount }.toFloat()
                listOf(cash, online)
            }
            MultiBarChart(monthLabels, monthlyBreakdown, colors = listOf(Color(0xFF4CAF50), Color(0xFF2196F3)))
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(10.dp).background(Color(0xFF4CAF50), CircleShape))
            Text(" Cash", style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.width(16.dp))
            Box(modifier = Modifier.size(10.dp).background(Color(0xFF2196F3), CircleShape))
            Text(" Online", style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.width(16.dp))
            Box(modifier = Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            Text(" Total", style = MaterialTheme.typography.labelSmall)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun FinanceTable(vaccinations: List<Vaccination>, inventory: List<Vaccine>, onMonthClick: (String) -> Unit = {}) {
    val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    
    // Group by month/year
    val grouped = vaccinations.groupBy {
        val cal = Calendar.getInstance().apply { 
            time = PatientUtils.parseDate(it.dateGiven) ?: Date(0)
        }
        String.format(Locale.US, "%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
    }.toList().sortedByDescending { it.first }

    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp)).padding(8.dp)) {
        // Header
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Month", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
            Text("Revenue", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
            Text("Profit", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
            Text("Imp. %", modifier = Modifier.weight(0.8f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
        }
        HorizontalDivider()

        var lastMonthProfit = 0.0
        
        // We need to calculate improvement from previous month, so we should process in chronological order then reverse for display
        val displayData = mutableListOf<FinanceSummaryItem>() 
        
        grouped.sortedBy { it.first }.forEach { (key, vaxList) ->
            val monthIdx = key.substringAfter("-").toInt()
            val year = key.substringBefore("-")
            val label = "${monthNames[monthIdx]} $year"
            val revenue = vaxList.sumOf { it.totalPaid }
            val netRate = vaxList.sumOf { v ->
                v.vaccineNames.sumOf { name ->
                    inventory.find { it.brandName == name }?.netRate ?: 0.0
                }
            }
            val profit = revenue - netRate
            displayData.add(FinanceSummaryItem(label, revenue, profit, key))
        }

        displayData.reversed().forEachIndexed { index, data ->
            val (label, revenue, profit, key) = data
            
            // Improvement over the month that followed it in chronological order
            val prevMonthData = if (index + 1 < displayData.size) displayData[displayData.size - 2 - index] else null
            val improvement = if (prevMonthData != null && prevMonthData.profit > 0) {
                ((profit - prevMonthData.profit) / prevMonthData.profit) * 100
            } else 0.0

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onMonthClick(key) }
                    .padding(vertical = 12.dp), 
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Text("₹${revenue.toInt()}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                Text("₹${profit.toInt()}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, color = if (profit > 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error)
                Text(
                    text = if (improvement == 0.0) "-" else String.format(Locale.getDefault(), "%.1f%%", improvement),
                    modifier = Modifier.weight(0.8f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.End,
                    color = if (improvement >= 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                )
            }
            if (index < displayData.size - 1) HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }

        // Total Row
        val totalRevenue = displayData.sumOf { it.revenue }
        val totalProfit = displayData.sumOf { it.profit }
        
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("TOTAL", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
            Text("₹${totalRevenue.toInt()}", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
            Text("₹${totalProfit.toInt()}", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, color = if (totalProfit > 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error)
            Text("-", modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
        }
    }
}

@Composable
fun MultiBarChart(labels: List<String>, dataSets: List<List<Float>>, colors: List<Color> = listOf(MaterialTheme.colorScheme.primary)) {
    val maxVal = dataSets.flatten().maxOrNull()?.takeIf { it > 0 } ?: 1f
    
    Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(180.dp).padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                dataSets.forEach { values ->
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        values.forEachIndexed { idx, v ->
                            val isTotal = values.size == 1 || (idx == 0 && colors[idx] == MaterialTheme.colorScheme.primary)
                            Box(
                                modifier = Modifier
                                    .width(if (isTotal) 12.dp else 16.dp) // Thinner if it's total
                                    .fillMaxHeight((v / maxVal).coerceIn(0.01f, 1f))
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(colors[idx % colors.size].copy(alpha = 0.6f))
                            )
                            if (idx < values.size - 1) Spacer(modifier = Modifier.width(2.dp))
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                labels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }

        // Line Chart on top
        Canvas(modifier = Modifier.fillMaxWidth().height(180.dp).padding(horizontal = 4.dp)) {
            val width = size.width
            val height = size.height
            val itemWidth = width / dataSets.size
            
            for (setIdx in 0 until dataSets[0].size) {
                val path = Path()
                var first = true
                
                dataSets.forEachIndexed { i, values ->
                    if (setIdx < values.size) {
                        val x = i * itemWidth + (itemWidth / 2)
                        val y = height - (values[setIdx] / maxVal) * height
                        
                        if (first) {
                            path.moveTo(x, y)
                            first = false
                        } else {
                            path.lineTo(x, y)
                        }
                        
                        drawCircle(
                            color = colors[setIdx % colors.size],
                            radius = 4.dp.toPx(),
                            center = Offset(x, y)
                        )
                    }
                }
                
                drawPath(
                    path = path,
                    color = colors[setIdx % colors.size],
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
}
