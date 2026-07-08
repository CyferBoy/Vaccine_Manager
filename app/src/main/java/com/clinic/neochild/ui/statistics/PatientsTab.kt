package com.clinic.neochild.ui.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clinic.neochild.data.model.Patient
import com.clinic.neochild.utils.Constants
import com.clinic.neochild.utils.PatientUtils
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PatientsTab(patients: List<Patient>) {
    val today = Calendar.getInstance()
    val currentMonth = today.get(Calendar.MONTH)
    val currentYear = today.get(Calendar.YEAR)
    
    val todayStr = SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(Date())
    val newPatientsToday = patients.count { it.registrationDate == todayStr }

    val newPatientsThisMonth = patients.count {
        val date = PatientUtils.parseDate(it.registrationDate)
        date != null && Calendar.getInstance().apply { time = date }.let { c ->
            c.get(Calendar.MONTH) == currentMonth && c.get(Calendar.YEAR) == currentYear
        }
    }

    val maleCount = patients.count { it.gender.equals("Male", true) }
    val femaleCount = patients.count { it.gender.equals("Female", true) }
    val otherCount = patients.size - maleCount - femaleCount

    val ageGroups = mutableMapOf(
        "0-6 Weeks" to 0,
        "6-14 Weeks" to 0,
        "3-9 Months" to 0,
        "9-18 Months" to 0,
        "18m - 5y" to 0,
        "Above 5y" to 0
    )

    patients.forEach { p ->
        val dob = PatientUtils.parseDate(p.dob) ?: return@forEach
        val diffMs = Date().time - dob.time
        val diffDays = (diffMs / (1000 * 60 * 60 * 24)).toInt()

        when {
            diffDays <= 42 -> ageGroups["0-6 Weeks"] = ageGroups["0-6 Weeks"]!! + 1
            diffDays <= 98 -> ageGroups["6-14 Weeks"] = ageGroups["6-14 Weeks"]!! + 1
            diffDays <= 270 -> ageGroups["3-9 Months"] = ageGroups["3-9 Months"]!! + 1
            diffDays <= 540 -> ageGroups["9-18 Months"] = ageGroups["9-18 Months"]!! + 1
            diffDays <= 1825 -> ageGroups["18m - 5y"] = ageGroups["18m - 5y"]!! + 1
            else -> ageGroups["Above 5y"] = ageGroups["Above 5y"]!! + 1
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Patient Analytics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            StatCardSmall(Modifier.weight(1f), "Total Patients", patients.size.toString(), Icons.Default.People, Color(0xFF2196F3))
            Spacer(modifier = Modifier.width(12.dp))
            StatCardSmall(Modifier.weight(1f), "New This Month", newPatientsThisMonth.toString(), Icons.Default.PersonAdd, Color(0xFF4CAF50))
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(modifier = Modifier.fillMaxWidth()) {
            StatCardSmall(Modifier.weight(1f), "New Today", newPatientsToday.toString(), Icons.Default.Today, Color(0xFF9C27B0))
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Gender Distribution", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                SimplePieChart(
                    data = listOf(maleCount.toFloat(), femaleCount.toFloat(), otherCount.toFloat()),
                    colors = listOf(Color(0xFF2196F3), Color(0xFFE91E63), Color(0xFF9E9E9E)),
                    labels = listOf("Male", "Female", "Other")
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Age Group Distribution", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        
        ageGroups.forEach { (label, count) ->
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    Text("$count", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                val progress = if (patients.isEmpty()) 0f else count.toFloat() / patients.size
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
