package com.clinic.neochild.ui.patient

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
import com.clinic.neochild.data.model.Consultation
import com.clinic.neochild.data.model.Patient
import com.clinic.neochild.data.model.Vaccination
import com.clinic.neochild.ui.components.*
import com.clinic.neochild.ui.theme.NeoChildTheme
import com.clinic.neochild.ui.viewmodel.PatientViewModel
import com.clinic.neochild.utils.PatientUtils.calculateAgeLabel
import com.clinic.neochild.utils.PatientUtils.cleanVaccineName
import com.clinic.neochild.utils.PatientUtils.formatDateForDisplay
import com.clinic.neochild.utils.PatientUtils.parseDate
import com.clinic.neochild.utils.ReceiptManager
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
    viewModel: PatientViewModel = hiltViewModel()
) {
    val allPatients by viewModel.allPatients.collectAsState()
    val allVaccinations by viewModel.allVaccinations.collectAsState()
    val context = LocalContext.current

    val patient = remember(patientId, allPatients) { allPatients.find { it.id == patientId } }
    val patientVaccinations = remember(patientId, allVaccinations) {
        allVaccinations.filter { it.patientId == patientId }.sortedByDescending { parseDate(it.dateGiven) }
    }
    
    var vaccinationToDelete by remember { mutableStateOf<Vaccination?>(null) }
    var showConsultationDialog by rememberSaveable { mutableStateOf(false) }

    DeleteConfirmationDialog(
        show = vaccinationToDelete != null,
        onDismiss = { vaccinationToDelete = null },
        onConfirm = {
            vaccinationToDelete?.id?.let { viewModel.deleteVaccination(it) }
            vaccinationToDelete = null
        },
        title = "Delete Record",
        message = "Are you sure you want to delete this vaccination record?"
    )

    if (showConsultationDialog && patient != null) {
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
                ReceiptManager.printConsultationReceipt(context, patient, consultation)
                showConsultationDialog = false
            }
        )
    }

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
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary, 
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            },
            floatingActionButton = {
                PatientActionFab(
                    onAddConsultation = { showConsultationDialog = true },
                    onAddVaccine = { onAddVaccine(patientId) }
                )
            }
        ) { paddingValues ->
            if (patient == null) {
                LoadingOrEmptyState(isLoading = allPatients.isEmpty(), message = "Patient not found", paddingValues = paddingValues)
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
private fun LoadingOrEmptyState(isLoading: Boolean, message: String, paddingValues: PaddingValues) {
    Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
        if (isLoading) CircularProgressIndicator()
        else Text(message)
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
        modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 88.dp, top = 16.dp)
    ) {
        item { PatientInfoSection(patient) }
        
        item {
            Text(
                text = "Vaccination History",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 16.dp)
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
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = patient.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        val ageLabel = remember(patient.dob) { calculateAgeLabel(patient.dob) }
        val dobDisplay = remember(patient.dob) { formatDateForDisplay(patient.dob) }
        val dobAgeValue = if (ageLabel != null) "$dobDisplay ($ageLabel)" else dobDisplay
        
        Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = dobAgeValue, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = patient.gender, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
        }

        ContactRow(icon = Icons.Default.Call, text = patient.phone, onClick = {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${patient.phone}")))
        })

        if (patient.alternatePhone.isNotBlank()) {
            ContactRow(icon = Icons.Default.Call, text = "${patient.alternatePhone} (Alt)", tint = MaterialTheme.colorScheme.secondary, onClick = {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${patient.alternatePhone}")))
            })
        }

        if (patient.address.isNotBlank()) {
            ContactRow(icon = Icons.Default.LocationOn, text = patient.address)
        }
    }
}

@Composable
private fun ContactRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tint: Color = MaterialTheme.colorScheme.primary, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = tint)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun PatientActionFab(onAddConsultation: () -> Unit, onAddVaccine: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(horizontalAlignment = Alignment.End) {
        if (expanded) {
            SmallFabAction(text = "Consultation", icon = Icons.Default.Receipt, color = MaterialTheme.colorScheme.secondary) {
                expanded = false
                onAddConsultation()
            }
            SmallFabAction(text = "+ Vaccine", icon = Icons.Default.Add, color = MaterialTheme.colorScheme.primary) {
                expanded = false
                onAddVaccine()
            }
        }
        
        FloatingActionButton(
            onClick = { expanded = !expanded },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(imageVector = if (expanded) Icons.Default.Close else Icons.Default.Add, contentDescription = "Expand")
        }
    }
}

@Composable
private fun SmallFabAction(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = color,
        contentColor = Color.White,
        modifier = Modifier.padding(bottom = 16.dp)
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = text)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaccinationRecordCard(
    vaccination: Vaccination,
    patient: Patient,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMarkAsDone: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).combinedClickable(
            onClick = { ReceiptManager.printReceipt(context, patient, vaccination) },
            onLongClick = { menuExpanded = true }
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(16.dp)) {
                VaccinationCardHeader(vaccination)
                
                if (vaccination.nxtVaccineNames.isNotEmpty()) {
                    val nextDisplayName = vaccination.nxtVaccineNames.joinToString(", ") { cleanVaccineName(it) }
                    Text(text = "Next: $nextDisplayName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }

                if (vaccination.batchNumbers.isNotEmpty()) {
                    Text(
                        text = "Batch: ${vaccination.batchNumbers.joinToString(", ")} | Exp: ${vaccination.expiryDates.joinToString(", ") { formatDateForDisplay(it) }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                if (vaccination.performedBy.isNotBlank()) {
                    Text(
                        text = "Added by: ${vaccination.performedBy}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                
                VaccinationCardDates(vaccination)
            }

            ActionDropdownMenu(
                expanded = menuExpanded,
                onDismiss = { menuExpanded = false },
                onEdit = onEdit,
                onDelete = onDelete,
                onMarkAsDone = if (!vaccination.isDone) onMarkAsDone else null,
                onDownload = {
                    scope.launch {
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
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val displayName = vaccination.vaccineNames.joinToString(", ") { cleanVaccineName(it) }
                Text(text = displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                
                if (vaccination.isDone) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = Color(0xFF4CAF50),
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
        }
        
        if (vaccination.totalPaid > 0) {
            PaymentInfoColumn(vaccination)
        } else if (vaccination.cost <= 0.0) {
            Text(text = "PRICE MISSING", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PaymentInfoColumn(vaccination: Vaccination) {
    Column(horizontalAlignment = Alignment.End) {
        Text(text = "₹${vaccination.totalPaid}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        val paymentLabel = when {
            vaccination.cashAmount > 0 && vaccination.onlineAmount > 0 -> "C: ₹${vaccination.cashAmount.toInt()} | O: ₹${vaccination.onlineAmount.toInt()}"
            vaccination.cashAmount > 0 -> "Cash"
            vaccination.onlineAmount > 0 -> "Online"
            else -> ""
        }
        if (paymentLabel.isNotEmpty()) {
            Text(text = paymentLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (vaccination.withFees) {
            Text(text = "With Fees", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        if (vaccination.doctorsAcc) {
            Text(text = "Doctors Acc", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun VaccinationCardDates(vaccination: Vaccination) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(text = "Date Given", style = MaterialTheme.typography.labelSmall)
            val dateGivenDisplay = remember(vaccination.dateGiven) { formatDateForDisplay(vaccination.dateGiven) }
            Text(text = dateGivenDisplay)
        }
        Column {
            Text(text = "Next Due Date", style = MaterialTheme.typography.labelSmall)
            val nextDueDateDisplay = remember(vaccination.nextDueDate) { formatDateForDisplay(vaccination.nextDueDate) }
            Text(text = nextDueDateDisplay, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ConsultationDialog(onDismiss: () -> Unit, onPrint: (Double, String, String) -> Unit) {
    var amount by rememberSaveable { mutableStateOf("400") }
    var notes by rememberSaveable { mutableStateOf("") }
    var followUpDate by rememberSaveable { mutableStateOf("") }

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
                StandardTextField(value = notes, onValueChange = { notes = it }, label = "Clinical Notes (Optional)", placeholder = "e.g. Fever, Cough...")
                DateDropdownPicker(label = "Next Follow-up Date (Optional)", currentDate = followUpDate, onDateSelected = { followUpDate = it })
            }
        },
        confirmButton = {
            StandardButton(onClick = { onPrint(amount.toDoubleOrNull() ?: 0.0, notes, followUpDate) }) { Text("Print Receipt") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
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
