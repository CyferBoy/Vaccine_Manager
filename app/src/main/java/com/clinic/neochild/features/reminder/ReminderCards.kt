package com.clinic.neochild.features.reminder

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clinic.neochild.domain.model.Patient
import com.clinic.neochild.domain.model.Vaccination
import com.clinic.neochild.core.utils.DateClassifier
import com.clinic.neochild.core.utils.DateCategory

@Composable
fun OverdueSummaryCard(overdueCount: Int) {
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
                Text("$overdueCount patients currently overdue (incl. Yesterday)", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(overdueCount.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = if (overdueCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DuePatientCard(
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
                    Column(horizontalAlignment = Alignment.End) {
                        val displayDate = if (vaccination.nextDueDate.isBlank()) "None" else DateClassifier.formatDisplay(vaccination.nextDueDate)
                        Surface(
                            color = if (vaccination.isDone) Color.LightGray else MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = displayDate,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (vaccination.isDone) Color.DarkGray else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        
                        val category = DateClassifier.classify(vaccination.nextDueDate)
                        val statusText = when {
                            vaccination.isDone && vaccination.nextDueDate.isBlank() -> "✅ Completed"
                            vaccination.isDone -> "✅ Done"
                            category is DateCategory.Overdue || category is DateCategory.Yesterday -> "⏰ Overdue"
                            else -> "⏰ Due"
                        }
                        val statusColor = if (vaccination.isDone) Color(0xFF4CAF50) else {
                            if (category is DateCategory.Overdue || category is DateCategory.Yesterday) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                        }
                        
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            modifier = Modifier.padding(top = 2.dp)
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
