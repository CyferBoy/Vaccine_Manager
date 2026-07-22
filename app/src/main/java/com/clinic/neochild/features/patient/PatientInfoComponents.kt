package com.clinic.neochild.features.patient

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.clinic.neochild.data.local.entity.AuditLogEntity
import com.clinic.neochild.domain.model.Patient
import com.clinic.neochild.domain.model.Vaccination
import com.clinic.neochild.core.utils.PatientUtils.calculateAgeLabel
import com.clinic.neochild.core.utils.PatientUtils.formatDateForDisplay
import com.clinic.neochild.core.designsystem.NeoChildTheme
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PatientDetailsContent(
    paddingValues: PaddingValues,
    patient: Patient,
    vaccinations: List<Vaccination>,
    onEditVaccination: (String) -> Unit,
    onDeleteVaccination: (Vaccination) -> Unit,
    onMarkAsDone: (Vaccination) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 80.dp, top = 16.dp)
    ) {
        item { PatientInfoSection(patient) }

        item {
            Text(
                text = "Vaccination History",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (vaccinations.isEmpty()) {
            item {
                Text(
                    text = "No vaccination records found.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            itemsIndexed(vaccinations, key = { _, v -> v.id }) { _, vaccination ->
                VaccinationRecordCard(
                    vaccination = vaccination,
                    patient = patient,
                    onEdit = { onEditVaccination(vaccination.id) },
                    onDelete = { onDeleteVaccination(vaccination) },
                    onMarkAsDone = { onMarkAsDone(vaccination) }
                )
            }
        }
    }
}

@Composable
fun PatientInfoSection(patient: Patient) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = patient.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }

            val clinicIdDisplay = if (patient.patientClinicId.isBlank()) "Not Assigned" else patient.patientClinicId
            InfoRow(Icons.Default.Badge, "Clinic ID: $clinicIdDisplay")

            val ageLabel = calculateAgeLabel(patient.dob)
            InfoRow(Icons.Default.Cake, "${formatDateForDisplay(patient.dob)} (${ageLabel ?: "Unknown Age"})")
            
            InfoRow(if (patient.gender == "Male") Icons.Default.Male else Icons.Default.Female, patient.gender)

            if (patient.phone.isNotBlank()) {
                InfoRow(
                    icon = Icons.Default.Phone,
                    text = patient.phone,
                    onClick = {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${patient.phone}"))
                        context.startActivity(intent)
                    }
                )
            }

            if (patient.alternatePhone.isNotBlank()) {
                InfoRow(
                    icon = Icons.Default.ContactPhone,
                    text = "Alt: ${patient.alternatePhone}",
                    onClick = {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${patient.alternatePhone}"))
                        context.startActivity(intent)
                    }
                )
            }

            if (patient.village.isNotBlank()) {
                InfoRow(Icons.Default.LocationCity, patient.village)
            }

            if (patient.address.isNotBlank()) {
                InfoRow(Icons.Default.Home, patient.address)
            }
            
            Text(
                text = "Registered on: ${formatDateForDisplay(patient.registrationDate)}",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.End),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text, 
            style = MaterialTheme.typography.bodyMedium,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun AuditLogDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    logs: List<AuditLogEntity>
) {
    if (!show) return

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Audit Log", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                Divider()
                
                if (logs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No audit logs found.")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        items(logs) { log ->
                            AuditLogItem(log)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AuditLogItem(log: AuditLogEntity) {
    val date = remember(log.timestamp) { SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(log.timestamp)) }
    val time = remember(log.timestamp) { SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(Date(log.timestamp)) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = log.action,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        val details = log.remarks ?: ""
        if (details.isNotBlank()) {
            Text(text = details, style = MaterialTheme.typography.bodySmall)
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(text = "By:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = log.user, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        
        if (log.device != null) {
            Text(text = "Device: ${log.device}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = time, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
    }
}

@Preview(showBackground = true)
@Composable
private fun PatientDetailsPreview() {
    NeoChildTheme {
        PatientDetailsContent(
            paddingValues = PaddingValues(0.dp),
            patient = Patient("1", "John Doe", "1234567890", "", "2020-01-01", "Male", "Old Hospital Road", "2024-01-01"),
            vaccinations = emptyList(),
            onEditVaccination = {},
            onDeleteVaccination = {},
            onMarkAsDone = {}
        )
    }
}
