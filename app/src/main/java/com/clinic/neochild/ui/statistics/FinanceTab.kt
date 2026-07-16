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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.clinic.neochild.domain.model.Vaccination
import com.clinic.neochild.domain.model.Vaccine
import com.clinic.neochild.core.ui.theme.NeoChildTheme
import com.clinic.neochild.data.mapper.FirestoreMappers
import com.clinic.neochild.core.utils.PatientUtils
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

data class FinanceSummaryItem(val label: String, val revenue: Double, val profit: Double, val key: String)

@Composable
fun FinanceTab(vaccinations: List<Vaccination>, onMonthClick: (String) -> Unit = {}) {
    var inventory by remember { mutableStateOf<List<Vaccine>>(emptyList()) }
    var filterMode by rememberSaveable { mutableStateOf("Overall") }

    LaunchedEffect(Unit) {
        FirebaseFirestore.getInstance().collection("inventory").get().addOnSuccessListener { result ->
            inventory = result.documents.mapNotNull { FirestoreMappers.toVaccine(it) }
        }
    }

    val availableYears = remember(vaccinations) {
        StatisticsUtils.getAvailableFinancialYears(vaccinations.map { it.dateGiven })
    }

    val filteredVaccinations = remember(vaccinations, filterMode) {
        vaccinations.filter { StatisticsUtils.isDateInFilter(it.dateGiven, filterMode) }
    }

    val financeStats = remember(filteredVaccinations, inventory) {
        calculateFinanceStats(filteredVaccinations, inventory)
    }

    FinanceContent(
        stats = financeStats,
        filterMode = filterMode,
        availableYears = availableYears,
        vaccinations = vaccinations,
        filteredVaccinations = filteredVaccinations,
        inventory = inventory,
        onFilterModeChange = { filterMode = it },
        onMonthClick = onMonthClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FinanceContent(
    stats: FinanceStatsData,
    filterMode: String,
    availableYears: List<String>,
    vaccinations: List<Vaccination>,
    filteredVaccinations: List<Vaccination>,
    inventory: List<Vaccine>,
    onFilterModeChange: (String) -> Unit,
    onMonthClick: (String) -> Unit
) {
    val mainOptions = remember(availableYears) { listOf("Overall") + availableYears.map { "FY $it" } }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        FinanceHeader(filterMode = filterMode, mainOptions = mainOptions, onFilterModeChange = onFilterModeChange)

        RevenueOverviewCard(revenue = stats.totalRevenue, filterMode = filterMode)

        Spacer(modifier = Modifier.height(24.dp))

        CashOnlineSummary(cash = stats.cashTotal, online = stats.onlineTotal)

        Spacer(modifier = Modifier.height(24.dp))

        Text("Financial Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        FinanceTable(filteredVaccinations, inventory, onMonthClick)

        Spacer(modifier = Modifier.height(32.dp))

        ChartsSection(filterMode = filterMode, availableYears = availableYears, vaccinations = vaccinations)

        Spacer(modifier = Modifier.height(16.dp))
        ChartLegend()
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun FinanceHeader(filterMode: String, mainOptions: List<String>, onFilterModeChange: (String) -> Unit) {
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
                            onFilterModeChange(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RevenueOverviewCard(revenue: Double, filterMode: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(if (filterMode == "Overall") "Overall Revenue" else "Revenue ($filterMode)", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
            Text("₹${String.format(Locale.getDefault(), "%,.0f", revenue)}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
private fun CashOnlineSummary(cash: Double, online: Double) {
    Row(modifier = Modifier.fillMaxWidth()) {
        FinanceStatItem(Modifier.weight(1f), "Cash Total", "₹${cash.toInt()}", Color(0xFF4CAF50))
        Spacer(modifier = Modifier.width(12.dp))
        FinanceStatItem(Modifier.weight(1f), "Online Total", "₹${online.toInt()}", Color(0xFF2196F3))
    }
}

@Composable
private fun ChartsSection(filterMode: String, availableYears: List<String>, vaccinations: List<Vaccination>) {
    if (filterMode == "Overall") {
        YearlyTrendCharts(availableYears = availableYears, vaccinations = vaccinations)
    } else {
        MonthlyTrendCharts(filterMode = filterMode, vaccinations = vaccinations)
    }
}

@Composable
private fun YearlyTrendCharts(availableYears: List<String>, vaccinations: List<Vaccination>) {
    Text("Total Revenue Trend (Yearly)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(16.dp))
    
    val yearlyTotals = availableYears.map { fy ->
        val revenue = vaccinations.filter { StatisticsUtils.isDateInFilter(it.dateGiven, "FY $fy") }.sumOf { it.totalPaid }.toFloat()
        listOf(revenue)
    }
    MultiBarChart(availableYears, yearlyTotals, colors = listOf(MaterialTheme.colorScheme.primary))

    Spacer(modifier = Modifier.height(32.dp))

    Text("Cash vs Online Breakdown (Yearly)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(16.dp))
    
    val yearlyBreakdown = availableYears.map { fy ->
        val fyVax = vaccinations.filter { StatisticsUtils.isDateInFilter(it.dateGiven, "FY $fy") }
        listOf(fyVax.sumOf { it.cashAmount }.toFloat(), fyVax.sumOf { it.onlineAmount }.toFloat())
    }
    MultiBarChart(availableYears, yearlyBreakdown, colors = listOf(Color(0xFF4CAF50), Color(0xFF2196F3)))
}

@Composable
private fun MonthlyTrendCharts(filterMode: String, vaccinations: List<Vaccination>) {
    val startYearShort = filterMode.substringAfter("FY ").substringBefore("-").toInt()
    val fyStartYear = if (startYearShort > 80) 1900 + startYearShort else 2000 + startYearShort
    val monthLabels = listOf("Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec", "Jan", "Feb", "Mar")
    
    val monthlyData = (4..15).map { mAdjusted ->
        val monthIdx = (mAdjusted - 1) % 12
        val yearAdjusted = if (mAdjusted > 12) fyStartYear + 1 else fyStartYear
        vaccinations.filter {
            val date = PatientUtils.parseDate(it.dateGiven) ?: return@filter false
            val cal = Calendar.getInstance().apply { time = date }
            cal.get(Calendar.MONTH) == monthIdx && cal.get(Calendar.YEAR) == yearAdjusted
        }
    }

    Text("Total Revenue Trend (Monthly)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(16.dp))
    MultiBarChart(monthLabels, monthlyData.map { listOf(it.sumOf { v -> v.totalPaid }.toFloat()) }, colors = listOf(MaterialTheme.colorScheme.primary))

    Spacer(modifier = Modifier.height(32.dp))

    Text("Cash vs Online Breakdown (Monthly)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(16.dp))
    MultiBarChart(monthLabels, monthlyData.map { listOf(it.sumOf { v -> v.cashAmount }.toFloat(), it.sumOf { v -> v.onlineAmount }.toFloat()) }, colors = listOf(Color(0xFF4CAF50), Color(0xFF2196F3)))
}

@Composable
private fun ChartLegend() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        LegendItem("Cash", Color(0xFF4CAF50))
        Spacer(modifier = Modifier.width(16.dp))
        LegendItem("Online", Color(0xFF2196F3))
        Spacer(modifier = Modifier.width(16.dp))
        LegendItem("Total", MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Text(" $label", style = MaterialTheme.typography.labelSmall)
    }
}

private data class FinanceStatsData(
    val totalRevenue: Double,
    val cashTotal: Double,
    val onlineTotal: Double,
    val totalProfit: Double
)

private fun calculateFinanceStats(vaccinations: List<Vaccination>, inventory: List<Vaccine>): FinanceStatsData {
    val revenue = vaccinations.sumOf { it.totalPaid }
    val cash = vaccinations.sumOf { it.cashAmount }
    val online = vaccinations.sumOf { it.onlineAmount }
    val netRate = vaccinations.sumOf { v ->
        v.vaccineNames.sumOf { name -> inventory.find { it.brandName == name }?.netRate ?: 0.0 }
    }
    return FinanceStatsData(revenue, cash, online, revenue - netRate)
}

@Preview(showBackground = true)
@Composable
private fun FinanceTabPreview() {
    NeoChildTheme {
        FinanceContent(
            stats = FinanceStatsData(10000.0, 6000.0, 4000.0, 2000.0),
            filterMode = "Overall",
            availableYears = listOf("23-24"),
            vaccinations = emptyList(),
            filteredVaccinations = emptyList(),
            inventory = emptyList(),
            onFilterModeChange = {},
            onMonthClick = {}
        )
    }
}

@Composable
fun FinanceTable(vaccinations: List<Vaccination>, inventory: List<Vaccine>, onMonthClick: (String) -> Unit = {}) {
    val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val grouped = vaccinations.groupBy {
        val cal = Calendar.getInstance().apply { time = PatientUtils.parseDate(it.dateGiven) ?: Date(0) }
        String.format(Locale.US, "%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
    }.toList().sortedBy { it.first }

    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp)).padding(8.dp)) {
        FinanceTableHeader()
        HorizontalDivider()

        val displayData = grouped.map { (key, list) ->
            val revenue = list.sumOf { it.totalPaid }
            val netRate = list.sumOf { v -> v.vaccineNames.sumOf { name -> inventory.find { it.brandName == name }?.netRate ?: 0.0 } }
            val monthIdx = key.substringAfter("-").toInt()
            val year = key.substringBefore("-")
            FinanceSummaryItem("${monthNames[monthIdx]} $year", revenue, revenue - netRate, key)
        }

        displayData.reversed().forEachIndexed { index, data ->
            val prevData = if (index + 1 < displayData.size) displayData[displayData.size - 2 - index] else null
            FinanceTableRow(data, prevData, onMonthClick)
            if (index < displayData.size - 1) HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }

        FinanceTableTotalRow(displayData)
    }
}

@Composable
private fun FinanceTableHeader() {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Month", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        Text("Revenue", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
        Text("Profit", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
        Text("Imp. %", modifier = Modifier.weight(0.8f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
    }
}

@Composable
private fun FinanceTableRow(data: FinanceSummaryItem, prevData: FinanceSummaryItem?, onMonthClick: (String) -> Unit) {
    val improvement = if (prevData != null && prevData.profit > 0) ((data.profit - prevData.profit) / prevData.profit) * 100 else 0.0

    Row(
        modifier = Modifier.fillMaxWidth().clickable { onMonthClick(data.key) }.padding(vertical = 12.dp), 
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(data.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text("₹${data.revenue.toInt()}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
        Text("₹${data.profit.toInt()}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, color = if (data.profit > 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error)
        Text(
            text = if (improvement == 0.0) "-" else String.format(Locale.getDefault(), "%.1f%%", improvement),
            modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End,
            color = if (improvement >= 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun FinanceTableTotalRow(dataList: List<FinanceSummaryItem>) {
    val totalRevenue = dataList.sumOf { it.revenue }
    val totalProfit = dataList.sumOf { it.profit }
    
    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("TOTAL", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
        Text("₹${totalRevenue.toInt()}", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
        Text("₹${totalProfit.toInt()}", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, color = if (totalProfit > 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error)
        Text("-", modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
    }
}

@Composable
fun MultiBarChart(labels: List<String>, dataSets: List<List<Float>>, colors: List<Color> = listOf(MaterialTheme.colorScheme.primary)) {
    val maxVal = remember(dataSets) { dataSets.flatten().maxOrNull()?.takeIf { it > 0 } ?: 1f }
    
    Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(180.dp).padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                dataSets.forEach { values ->
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                        values.forEachIndexed { idx, v ->
                            val barWidth = if (values.size == 1) 12.dp else 16.dp
                            Box(
                                modifier = Modifier
                                    .width(barWidth)
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
                    Text(text = label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1)
                }
            }
        }

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
                        if (first) { path.moveTo(x, y); first = false } else { path.lineTo(x, y) }
                        drawCircle(color = colors[setIdx % colors.size], radius = 4.dp.toPx(), center = Offset(x, y))
                    }
                }
                drawPath(path = path, color = colors[setIdx % colors.size], style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
}
