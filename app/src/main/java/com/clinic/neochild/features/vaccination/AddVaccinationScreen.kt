package com.clinic.neochild.features.vaccination

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
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
                    Text("Multiple batches found. FEFO (First Expiry, First Out) suggests the first one.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    batchesToSelectFrom.forEach { batch ->
                        DropdownMenuItem(
                            text = { 
                                Column {
                                    Text("Batch: ${batch.batchNumber} (Stock: ${batch.remainingQuantity})")
                                    Text("Expires: ${batch.expiryDate}", style = MaterialTheme.typography.labelSmall)
                                }
                            },
                            onClick = {
                                val v = currentVaccineSelecting!!
                                selectedVaccines = selectedVaccines + v.brandName
                                batchNumbers = batchNumbers + batch.batchNumber
                                expiryDates = expiryDates + batch.expiryDate
                                selectedVaccineIds = selectedVaccineIds + v.id
                                showBatchSelectionDialog = false
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
        selectedVaccines = selectedVaccines,
        onVaccineSelected = { v ->
            if (!selectedVaccines.contains(v.brandName)) {
                val batches = uiState.activeBatches[v.id] ?: emptyList()
                if (batches.size == 1) {
                    val batch = batches[0]
                    selectedVaccines = selectedVaccines + v.brandName
                    batchNumbers = batchNumbers + batch.batchNumber
                    expiryDates = expiryDates + batch.expiryDate
                    selectedVaccineIds = selectedVaccineIds + v.id
                } else if (batches.size > 1) {
                    currentVaccineSelecting = v
                    batchesToSelectFrom = batches
                    showBatchSelectionDialog = true
                } else {
                    // Fallback if stock sync is slightly off but dropdown allowed click
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
