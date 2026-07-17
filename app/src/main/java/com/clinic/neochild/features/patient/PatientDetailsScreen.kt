package com.clinic.neochild.features.patient

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.clinic.neochild.domain.model.Consultation
import com.clinic.neochild.domain.model.Patient
import com.clinic.neochild.domain.model.Vaccination
import com.clinic.neochild.core.common.*
import com.clinic.neochild.core.designsystem.NeoChildTheme
import com.clinic.neochild.features.patient.PatientViewModel
import com.clinic.neochild.core.utils.PatientUtils.calculateAgeLabel
import com.clinic.neochild.core.utils.PatientUtils.cleanVaccineName
import com.clinic.neochild.core.utils.PatientUtils.formatDateForDisplay
import com.clinic.neochild.core.utils.PatientUtils.parseDate
import com.clinic.neochild.core.utils.ReceiptManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientDetailsScreen(
    patientId: String, 
    onBack: () -> Unit = {}, 
    onAddVaccine: (String) -> Unit = {},
    onEditVaccination: (String) -> Unit = {},
    onNavigateToTimeline: (String) -> Unit = {},
    viewModel: PatientViewModel = hiltViewModel()
) {
    val allPatients by viewModel.allPatients.collectAsState()
    val patient = remember(patientId, allPatients) { allPatients.find { it.id == patientId } }
    
    val allVaccinations by viewModel.allVaccinations.collectAsState()
    
    // Derived state for patient vaccinations
    val patientVaccinations = remember(patientId, allVaccinations) {
        allVaccinations.filter { it.patientId == patientId }.sortedByDescending { parseDate(it.dateGiven) }
    }

    var vaccinationToDelete by remember { mutableStateOf<Vaccination?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    DeleteConfirmationDialog(
        show = vaccinationToDelete != null,
        onDismiss = { vaccinationToDelete = null },
        onConfirm = {
            vaccinationToDelete?.id?.let { viewModel.deleteVaccination(it) }
            vaccinationToDelete = null
        },
        title = "Delete Vaccination",
        message = "Are you sure you want to delete this vaccination record?"
    )

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Patient Details") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { onNavigateToTimeline(patientId) }) {
                            Icon(Icons.Default.History, contentDescription = "Timeline", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { onAddVaccine(patientId) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Vaccination")
                }
            }
        ) { paddingValues ->
            if (patient == null) {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Text("Patient not found", style = MaterialTheme.typography.titleLarge)
                }
            } else {
                PatientDetailsContent(
                    paddingValues = paddingValues,
                    patient = patient,
                    vaccinations = patientVaccinations,
                    onEditVaccination = onEditVaccination,
                    onDeleteVaccination = { vaccinationToDelete = it },
                    onMarkAsDone = { viewModel.markAsDone(it) }
                )
            }
        }
    }
}

@Composable
private fun PatientDetailsContent(
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
private fun PatientInfoSection(patient: Patient) {
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

            if (patient.patientClinicId.isNotBlank()) {
                InfoRow(Icons.Default.Badge, "Clinic ID: ${patient.patientClinicId}")
            }

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
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: (() -> Unit)? = null) {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaccinationRecordCard(
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
private fun VaccinationCardHeader(vaccination: Vaccination) {
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
private fun PaymentInfoColumn(vaccination: Vaccination) {
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
private fun VaccinationCardDates(vaccination: Vaccination) {
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
