package com.clinic.neochild.features.patient

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.clinic.neochild.domain.model.Patient
import com.clinic.neochild.domain.model.Vaccination
import com.clinic.neochild.core.utils.PatientUtils.cleanVaccineName
import com.clinic.neochild.core.utils.PatientUtils.formatDateForDisplay
import com.clinic.neochild.core.utils.ReceiptManager
import com.clinic.neochild.core.ui.ActionDropdownMenu
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VaccinationRecordCard(
    vaccination: Vaccination,
    patient: Patient,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMarkAsDone: (Vaccination) -> Unit
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { ReceiptManager.printReceipt(context, patient, vaccination) },
                onLongClick = { menuExpanded = true }
            ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            VaccinationCardHeader(vaccination)
            
            if (vaccination.nxtVaccineNames.isNotEmpty()) {
                val nextDisplayName = vaccination.nxtVaccineNames.joinToString(", ") { cleanVaccineName(it) }
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Next: $nextDisplayName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            if (vaccination.batchNumbers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Batch: ${vaccination.batchNumbers.joinToString(", ")} | Exp: ${vaccination.expiryDates.joinToString(", ")}", style = MaterialTheme.typography.labelSmall)
            }

            if (vaccination.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Notes: ${vaccination.notes}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (vaccination.performedBy.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Added by: ${vaccination.performedBy}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Light)
            }

            Spacer(modifier = Modifier.height(8.dp))
            VaccinationCardDates(vaccination)
        }

        Box(modifier = Modifier.align(Alignment.End)) {
            ActionDropdownMenu(
                expanded = menuExpanded,
                onDismiss = { menuExpanded = false },
                onEdit = onEdit,
                onDelete = onDelete,
                onMarkAsDone = if (!vaccination.isDone) { { onMarkAsDone(vaccination) } } else null,
                onDownload = { 
                    (context as? androidx.activity.ComponentActivity)?.lifecycleScope?.launch {
                        ReceiptManager.downloadReceipt(context, patient, vaccination)
                    }
                }
            )
        }
    }
}

@Composable
fun VaccinationCardHeader(vaccination: Vaccination) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        val displayName = vaccination.vaccineNames.joinToString(", ") { cleanVaccineName(it) }
        Text(text = displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        
        if (vaccination.isDone) {
            Icon(Icons.Default.CheckCircle, contentDescription = "Done", tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
        } else {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "DUE", 
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (vaccination.totalPaid > 0) {
            PaymentInfoColumn(vaccination)
        } else if (vaccination.cost <= 0.0) {
             Text(text = "FREE", style = MaterialTheme.typography.labelMedium, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PaymentInfoColumn(vaccination: Vaccination) {
    Column(horizontalAlignment = Alignment.End) {
        Text(text = "₹${vaccination.totalPaid}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        val method = when {
            vaccination.cashAmount > 0 && vaccination.onlineAmount > 0 -> "C: ₹${vaccination.cashAmount.toInt()} | O: ₹${vaccination.onlineAmount.toInt()}"
            vaccination.cashAmount > 0 -> "Cash"
            vaccination.onlineAmount > 0 -> "Online"
            else -> ""
        }
        if (method.isNotBlank()) {
            Text(text = method, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (vaccination.withFees) {
            Text(text = "+ Fees", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
        if (vaccination.doctorsAcc) {
            Text(text = "Dr. Acc", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
fun VaccinationCardDates(vaccination: Vaccination) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(text = "DATE GIVEN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val dateGivenDisplay = remember(vaccination.dateGiven) { formatDateForDisplay(vaccination.dateGiven) }
            Text(text = dateGivenDisplay, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        }
        if (vaccination.nextDueDate.isNotBlank()) {
            Column(horizontalAlignment = Alignment.End) {
                Text(text = "NEXT DUE DATE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val nextDueDateDisplay = remember(vaccination.nextDueDate) { formatDateForDisplay(vaccination.nextDueDate) }
                Text(text = nextDueDateDisplay, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
