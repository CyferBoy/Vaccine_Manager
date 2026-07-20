package com.clinic.neochild.features.vaccination

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import com.clinic.neochild.core.utils.InventoryUtils
import com.clinic.neochild.domain.model.Vaccine
import com.clinic.neochild.features.patient.PatientViewModel
import com.clinic.neochild.core.constants.Constants
import com.clinic.neochild.core.utils.ReceiptManager
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AddVaccinationScreen(
    initialPatientId: String = "", 
    vaccinationId: String? = null,
    onBack: () -> Unit = {},
    onScheduleFollowUp: (String, String, List<String>) -> Unit = { _, _, _ -> },
    patientViewModel: PatientViewModel = hiltViewModel(),
    viewModel: AddVaccinationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val allPatients by patientViewModel.allPatients.collectAsState()
    val existingVaccination by viewModel.vaccination.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showFollowUpDialog by remember { mutableStateOf(false) }
    var showBatchSelectionDialog by remember { mutableStateOf(false) }
    var batchesToSelectFrom by remember { mutableStateOf<List<com.clinic.neochild.data.local.entity.VaccineBatchEntity>>(emptyList()) }
    var currentVaccineSelecting by remember { mutableStateOf<Vaccine?>(null) }

    // Form State
    var patientId by rememberSaveable { mutableStateOf(initialPatientId) }
    var selectedVaccines by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedVaccineIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var batchNumbers by remember { mutableStateOf<List<String>>(emptyList()) }
    var expiryDates by remember { mutableStateOf<List<String>>(emptyList()) }
    var nextBrandSearch by rememberSaveable { mutableStateOf("") }
    
    val today = remember { SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(Date()) }
    var dateGiven by rememberSaveable { mutableStateOf(today) }
    var nextDueDate by rememberSaveable { mutableStateOf("") }
    
    var cost by rememberSaveable { mutableStateOf("") }
    var cashAmount by rememberSaveable { mutableStateOf("") }
    var onlineAmount by rememberSaveable { mutableStateOf("") }
    var withFees by rememberSaveable { mutableStateOf(false) }
    var doctorsAcc by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(vaccinationId) {
        if (vaccinationId != null) {
            viewModel.loadVaccination(vaccinationId)
        }
    }

    LaunchedEffect(existingVaccination) {
        existingVaccination?.let { v ->
            patientId = v.patientId
            selectedVaccines = v.vaccineNames
            batchNumbers = v.batchNumbers
            expiryDates = v.expiryDates
            nextBrandSearch = v.nxtVaccineNames.joinToString(", ")
            dateGiven = v.dateGiven
            nextDueDate = v.nextDueDate
            cost = if (v.cost % 1.0 == 0.0) v.cost.toInt().toString() else v.cost.toString()
            cashAmount = if (v.cashAmount % 1.0 == 0.0) v.cashAmount.toInt().toString() else v.cashAmount.toString()
            onlineAmount = if (v.onlineAmount % 1.0 == 0.0) v.onlineAmount.toInt().toString() else v.onlineAmount.toString()
            withFees = v.withFees
            doctorsAcc = v.doctorsAcc
        }
    }

    val totalPaid = remember(cashAmount, onlineAmount) {
        (cashAmount.toDoubleOrNull() ?: 0.0) + (onlineAmount.toDoubleOrNull() ?: 0.0)
    }

    LaunchedEffect(totalPaid) {
        if (totalPaid > 0) {
            cost = if (totalPaid % 1.0 == 0.0) totalPaid.toInt().toString() else totalPaid.toString()
        }
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            showFollowUpDialog = true
        }
    }

    if (showFollowUpDialog) {
        AlertDialog(
            onDismissRequest = { 
                viewModel.resetSaveState()
                onBack() 
            },
            title = { Text("Vaccination Saved") },
            text = { Text("Vaccination saved successfully. Would you like to schedule a follow-up?") },
            confirmButton = {
                Button(onClick = {
                    val saved = uiState.savedVaccination
                    if (saved != null) {
                        onScheduleFollowUp(saved.patientId, saved.id, saved.nxtVaccineNames)
                    }
                    viewModel.resetSaveState()
                }) { Text("Schedule Follow-up") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.resetSaveState()
                    onBack()
                }) { Text("Finish") }
            }
        )
    }

    if (showBatchSelectionDialog && currentVaccineSelecting != null) {
        AlertDialog(
            onDismissRequest = { showBatchSelectionDialog = false },
            title = { Text("Select Batch for ${currentVaccineSelecting?.brandName}") },
            text = {
                Column {
                    Text("Select an active batch. FEFO (First Expiry, First Out) prioritizes earliest valid expiry.", style = MaterialTheme.typography.bodySmall)
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = uiState.showExpiredBatches, onCheckedChange = { viewModel.toggleShowExpiredBatches(it) })
                        Text("Show Expired Batches", style = MaterialTheme.typography.labelMedium)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    
                    batchesToSelectFrom.forEach { batch ->
                        val isExpired = InventoryUtils.isExpired(batch.expiryDate)
                        val isNearExpiry = !isExpired && InventoryUtils.isNearExpiry(batch.expiryDate)
                        
                        DropdownMenuItem(
                            text = { 
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Batch: ${batch.batchNumber}",
                                            color = if (isExpired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (isExpired) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("(EXPIRED)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                        } else if (isNearExpiry) {
                                            val days = InventoryUtils.getDaysUntilExpiry(batch.expiryDate)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(" (⚠ Expires in $days days)", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFA000))
                                        }
                                    }
                                    Text("Expires: ${batch.expiryDate} | Stock: ${batch.remainingQuantity}", style = MaterialTheme.typography.labelSmall)
                                }
                            },
                            onClick = {
                                if (isExpired) {
                                    Toast.makeText(context, "Cannot select expired batch for vaccination", Toast.LENGTH_LONG).show()
                                } else {
                                    val v = currentVaccineSelecting!!
                                    selectedVaccines = selectedVaccines + v.brandName
                                    batchNumbers = batchNumbers + batch.batchNumber
                                    expiryDates = expiryDates + batch.expiryDate
                                    selectedVaccineIds = selectedVaccineIds + v.id
                                    showBatchSelectionDialog = false
                                }
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showBatchSelectionDialog = false }) { Text("Cancel") }
            }
        )
    }

    AddVaccinationContent(
        isEdit = vaccinationId != null,
        onBack = onBack,
        patientId = patientId,
        onPatientIdChange = { if (initialPatientId.isEmpty()) patientId = it },
        isPatientIdEnabled = initialPatientId.isEmpty(),
        inventory = uiState.inventory,
        lowStockThreshold = uiState.lowStockThreshold,
        selectedVaccines = selectedVaccines,
        onVaccineSelected = { v ->
            if (!selectedVaccines.contains(v.brandName)) {
                val allBatches = uiState.activeBatches[v.id] ?: emptyList()
                val validBatches = allBatches.filter { !InventoryUtils.isExpired(it.expiryDate) }

                if (validBatches.size == 1) {
                    val batch = validBatches[0]
                    selectedVaccines = selectedVaccines + v.brandName
                    batchNumbers = batchNumbers + batch.batchNumber
                    expiryDates = expiryDates + batch.expiryDate
                    selectedVaccineIds = selectedVaccineIds + v.id
                    
                    if (InventoryUtils.isNearExpiry(batch.expiryDate)) {
                        val days = InventoryUtils.getDaysUntilExpiry(batch.expiryDate)
                        Toast.makeText(context, "Warning: Selected batch expires in $days days", Toast.LENGTH_SHORT).show()
                    }
                } else if (validBatches.size > 1 || (uiState.showExpiredBatches && allBatches.isNotEmpty())) {
                    currentVaccineSelecting = v
                    batchesToSelectFrom = allBatches
                    showBatchSelectionDialog = true
                } else if (allBatches.any { InventoryUtils.isExpired(it.expiryDate) }) {
                    Toast.makeText(context, "All available batches for ${v.brandName} have expired!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "No active batches for ${v.brandName}", Toast.LENGTH_SHORT).show()
                }
            }
        },
        onRemoveVaccine = { index ->
            selectedVaccines = selectedVaccines.toMutableList().apply { removeAt(index) }
            batchNumbers = batchNumbers.toMutableList().apply { removeAt(index) }
            expiryDates = expiryDates.toMutableList().apply { removeAt(index) }
        },
        nextBrandSearch = nextBrandSearch,
        onNextBrandChange = { nextBrandSearch = it },
        dateGiven = dateGiven,
        onDateGivenChange = { dateGiven = it },
        nextDueDate = nextDueDate,
        onNextDueDateChange = { nextDueDate = it },
        cashAmount = cashAmount,
        onCashChange = { cashAmount = it },
        onlineAmount = onlineAmount,
        onOnlineChange = { onlineAmount = it },
        totalPaid = totalPaid,
        cost = cost,
        onCostChange = { cost = it },
        withFees = withFees,
        onFeesToggle = { withFees = it },
        doctorsAcc = doctorsAcc,
        onAccToggle = { doctorsAcc = it },
        isLoading = uiState.isLoading,
        onSave = {
            if (VaccinationValidator.validateForm(context, patientId, selectedVaccines)) {
                val user = FirebaseAuth.getInstance().currentUser?.email ?: "Unknown"
                val v = VaccinationValidator.createVaccination(vaccinationId, patientId, selectedVaccines, nextBrandSearch, dateGiven, nextDueDate, cost, cashAmount, onlineAmount, totalPaid, withFees, doctorsAcc, batchNumbers, expiryDates, user)
                viewModel.saveVaccination(v, vaccinationId == null, selectedVaccineIds) {}
            }
        },
        onSaveAndDownload = {
            val patient = allPatients.find { it.id == patientId }
            if (VaccinationValidator.validateForm(context, patientId, selectedVaccines) && patient != null) {
                val user = FirebaseAuth.getInstance().currentUser?.email ?: "Unknown"
                val v = VaccinationValidator.createVaccination(vaccinationId, patientId, selectedVaccines, nextBrandSearch, dateGiven, nextDueDate, cost, cashAmount, onlineAmount, totalPaid, withFees, doctorsAcc, batchNumbers, expiryDates, user)
                viewModel.saveVaccination(v, vaccinationId == null, selectedVaccineIds) {
                    scope.launch {
                        ReceiptManager.downloadReceipt(context, patient, v)
                    }
                }
            } else if (patient == null) {
                Toast.makeText(context, "Patient not found", Toast.LENGTH_SHORT).show()
            }
        }
    )
}
