package com.clinic.neochild.ui.patient

import android.content.Intent
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clinic.neochild.data.model.Consultation
import com.clinic.neochild.data.model.Vaccination
import com.clinic.neochild.ui.components.*
import com.clinic.neochild.ui.viewmodel.PatientViewModel
import com.clinic.neochild.utils.PatientUtils.calculateAgeLabel
import com.clinic.neochild.utils.PatientUtils.cleanVaccineName
import com.clinic.neochild.utils.PatientUtils.formatDateForDisplay
import com.clinic.neochild.utils.PatientUtils.parseDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientDetailsScreen(
    patientId: String, 
    onBack: () -> Unit = {}, 
    onAddVaccine: (String) -> Unit = {},
    onEditVaccination: (String) -> Unit = {},
    viewModel: PatientViewModel = viewModel()
) {
    val allPatients by viewModel.allPatients.collectAsState()
    val allVaccinations by viewModel.allVaccinations.collectAsState()
    
    val patient = remember(patientId, allPatients) {
        allPatients.find { it.id == patientId }
    }
    
    val patientVaccinations = remember(patientId, allVaccinations) {
        allVaccinations.asSequence().filter { it.patientId == patientId }
            .sortedByDescending { parseDate(it.dateGiven) }.toList()
    }
    
    var showDeletePatientDialog by remember { mutableStateOf(false) }
    var vaccinationToDelete by remember { mutableStateOf<Vaccination?>(null) }
    var showConsultationDialog by remember { mutableStateOf(false) }
    var isFabExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current

    DeleteConfirmationDialog(
        show = showDeletePatientDialog,
        onDismiss = { showDeletePatientDialog = false },
        onConfirm = {
            showDeletePatientDialog = false
            viewModel.deletePatient(patientId)
            onBack()
        },
        title = "Delete Patient",
        message = "Are you sure you want to delete this patient and all their records? This action cannot be undone."
    )

    DeleteConfirmationDialog(
        show = vaccinationToDelete != null,
        onDismiss = { vaccinationToDelete = null },
        onConfirm = {
            val id = vaccinationToDelete?.id ?: ""
            vaccinationToDelete = null
            viewModel.deleteVaccination(id)
        },
        title = "Delete Record",
        message = "Are you sure you want to delete this vaccination record?"
    )

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                TopAppBar(
                    title = { Text("Patient Details") },
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
            },
            floatingActionButton = {
                Column(horizontalAlignment = Alignment.End) {
                    if (isFabExpanded) {
                        FloatingActionButton(
                            onClick = { 
                                isFabExpanded = false
                                showConsultationDialog = true 
                            },
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Receipt, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "Consultation")
                            }
                        }
                        FloatingActionButton(
                            onClick = { 
                                isFabExpanded = false
                                onAddVaccine(patientId) 
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "+ Vaccine")
                            }
                        }
                    }
                    
                    FloatingActionButton(
                        onClick = { isFabExpanded = !isFabExpanded },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(
                            imageVector = if (isFabExpanded) Icons.Default.Close else Icons.Default.Add,
                            contentDescription = "Expand"
                        )
                    }
                }
            }
        ) { paddingValues ->
            if (allPatients.isEmpty() && (patient == null)) {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (patient == null) {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Text("Patient not found")
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 88.dp, top = 16.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = patient.name,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        
                        val ageLabel = calculateAgeLabel(patient.dob)
                        val dobDisplay = formatDateForDisplay(patient.dob)
                        val dobAgeValue = if (ageLabel != null) "$dobDisplay ($ageLabel)" else dobDisplay
                        
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = dobAgeValue, style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(text = patient.gender, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
                        }

                        Row(
                            modifier = Modifier.padding(vertical = 8.dp).clickable {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${patient.phone}"))
                                context.startActivity(intent)
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Call",
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = patient.phone,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (patient.alternatePhone.isNotBlank()) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp).clickable {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${patient.alternatePhone}"))
                                    context.startActivity(intent)
                                },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Call,
                                    contentDescription = "Call Alternate",
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${patient.alternatePhone} (Alt)",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        if (patient.address.isNotBlank()) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = "Address",
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = patient.address,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Text(
                            text = "Vaccination History",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }
                    
                    if (patientVaccinations.isEmpty()) {
                        item {
                            Text(
                                text = "No vaccination records found.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        itemsIndexed(patientVaccinations) { _, vaccination ->
                            VaccinationCard(
                                vaccination = vaccination,
                                patient = patient,
                                onEdit = { onEditVaccination(vaccination.id) },
                                onDelete = { vaccinationToDelete = vaccination }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showConsultationDialog && (patient != null)) {
        ConsultationDialog(
            onDismiss = { showConsultationDialog = false },
            onPrint = { amount, notes, followUp ->
                val consultation = Consultation(
                    id = UUID.randomUUID().toString(),
                    patientId = patientId,
                    date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                    amount = amount,
                    notes = notes,
                    nextFollowUpDate = followUp
                )
                com.clinic.neochild.utils.ReceiptManager.printConsultationReceipt(context, patient, consultation)
                showConsultationDialog = false
            }
        )
    }
}

@Composable
fun ConsultationDialog(
    onDismiss: () -> Unit,
    onPrint: (Double, String, String) -> Unit
) {
    var amount by remember { mutableStateOf("400") }
    var notes by remember { mutableStateOf("") }
    var followUpDate by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Consultation Details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StandardTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = "Consultation Fee (₹)",
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                
                StandardTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = "Clinical Notes (Optional)",
                    placeholder = "e.g. Fever, Cough..."
                )
                
                DateDropdownPicker(
                    label = "Next Follow-up Date (Optional)",
                    currentDate = followUpDate,
                    onDateSelected = { followUpDate = it }
                )
            }
        },
        confirmButton = {
            StandardButton(
                onClick = {
                    val fee = amount.toDoubleOrNull() ?: 0.0
                    onPrint(fee, notes, followUpDate)
                }
            ) {
                Text("Print Receipt")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VaccinationCard(
    vaccination: Vaccination,
    patient: com.clinic.neochild.data.model.Patient,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .combinedClickable(
                onClick = { 
                    com.clinic.neochild.utils.ReceiptManager.printReceipt(context, patient, vaccination)
                },
                onLongClick = { menuExpanded = true }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    val displayName = vaccination.vaccineNames.joinToString(", ") { cleanVaccineName(it) }
                    
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (vaccination.totalPaid > 0) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "₹${vaccination.totalPaid}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            val paymentLabel = when {
                                vaccination.cashAmount > 0 && vaccination.onlineAmount > 0 -> 
                                    "C: ₹${vaccination.cashAmount.toInt()} | O: ₹${vaccination.onlineAmount.toInt()}"
                                vaccination.cashAmount > 0 -> "Cash"
                                vaccination.onlineAmount > 0 -> "Online"
                                else -> ""
                            }
                            if (paymentLabel.isNotEmpty()) {
                                Text(
                                    text = paymentLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (vaccination.withFees) {
                                Text(
                                    text = "With Fees",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (vaccination.doctorsAcc) {
                                Text(
                                    text = "Doctors Acc",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else if (vaccination.cost <= 0.0) {
                        Text(
                            text = "PRICE MISSING",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                if (vaccination.nxtVaccineNames.isNotEmpty()) {
                    val nextDisplayName = vaccination.nxtVaccineNames.joinToString(", ") { cleanVaccineName(it) }
                    Text(
                        text = "Next: $nextDisplayName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                if (vaccination.batchNumbers.isNotEmpty()) {
                    Text(
                        text = "Batch: ${vaccination.batchNumbers.joinToString(", ")} | Exp: ${vaccination.expiryDates.joinToString(", ") { formatDateForDisplay(it) }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "Date Given", style = MaterialTheme.typography.labelSmall)
                        Text(text = formatDateForDisplay(vaccination.dateGiven))
                    }
                    Column {
                        Text(text = "Next Due Date", style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = formatDateForDisplay(vaccination.nextDueDate),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            ActionDropdownMenu(
                expanded = menuExpanded,
                onDismiss = { menuExpanded = false },
                onEdit = onEdit,
                onDelete = onDelete,
                onDownload = {
                    com.clinic.neochild.utils.ReceiptManager.downloadReceipt(context, patient, vaccination)
                }
            )
        }
    }
}
