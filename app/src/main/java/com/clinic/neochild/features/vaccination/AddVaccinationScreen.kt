package com.clinic.neochild.features.vaccination

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    initialVaccineName: String? = null,
    vaccinationId: String? = null,
    onBack: () -> Unit = {},
    onScheduleFollowUp: (String, String, List<String>) -> Unit = { _, _, _ -> },
    patientViewModel: PatientViewModel = hiltViewModel(),
    viewModel: AddVaccinationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val allPatients by patientViewModel.allPatients.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showFollowUpDialog by remember { mutableStateOf(false) }
    var showBatchSelectionDialog by remember { mutableStateOf(false) }
    var currentVaccineSelecting by remember { mutableStateOf<Vaccine?>(null) }

    // Initialize initial data
    LaunchedEffect(initialPatientId) {
        if (initialPatientId.isNotEmpty()) {
            viewModel.onPatientIdChange(initialPatientId)
        }
    }

    LaunchedEffect(vaccinationId) {
        if (vaccinationId != null) {
            viewModel.loadVaccination(vaccinationId)
        }
    }

    val currentVaccination by viewModel.vaccination.collectAsState()
    LaunchedEffect(currentVaccination) {
        currentVaccination?.let { viewModel.prefillForm(it) }
    }

    LaunchedEffect(Unit) {
        val today = SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(Date())
        viewModel.initializeDates(today)
    }

    // Auto-trigger batch selection if initial vaccine provided
    LaunchedEffect(initialVaccineName, uiState.inventory) {
        if (vaccinationId == null && initialVaccineName != null && uiState.selectedVaccines.isEmpty() && uiState.inventory.isNotEmpty()) {
            val matching = uiState.inventory.find { it.brandName.equals(initialVaccineName, ignoreCase = true) }
            if (matching != null) {
                currentVaccineSelecting = matching
                showBatchSelectionDialog = true
            }
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
        val batches = uiState.activeBatches[currentVaccineSelecting?.id] ?: emptyList()
        
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
                    
                    if (batches.isEmpty()) {
                        Text("No active batches available.", color = MaterialTheme.colorScheme.error)
                    }

                    batches.forEach { batch ->
                        val isExpired = InventoryUtils.isExpired(batch.expiryDate)
                        val isExpiringToday = !isExpired && InventoryUtils.isExpiringToday(batch.expiryDate)
                        val isNearExpiry = !isExpired && !isExpiringToday && InventoryUtils.isNearExpiry(batch.expiryDate)
                        
                        DropdownMenuItem(
                            text = { 
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Batch: ${batch.batchNumber}",
                                            color = if (isExpired || isExpiringToday) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (isExpired) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("(EXPIRED)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                        } else if (isExpiringToday) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("(EXPIRES TODAY)", style = MaterialTheme.typography.labelSmall, color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
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
                                    currentVaccineSelecting?.let { viewModel.onVaccineSelected(it, batch) }
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
        patientId = uiState.patientId,
        onPatientIdChange = viewModel::onPatientIdChange,
        isPatientIdEnabled = initialPatientId.isEmpty(),
        inventory = uiState.inventory,
        lowStockThreshold = uiState.lowStockThreshold,
        selectedVaccines = uiState.selectedVaccines,
        onVaccineSelected = { v ->
            if (!uiState.selectedVaccines.contains(v.brandName)) {
                currentVaccineSelecting = v
                showBatchSelectionDialog = true
            }
        },
        onRemoveVaccine = viewModel::onRemoveVaccine,
        nextBrandSearch = uiState.nextBrandSearch,
        onNextBrandChange = viewModel::onNextBrandChange,
        dateGiven = uiState.dateGiven,
        onDateGivenChange = viewModel::onDateGivenChange,
        nextDueDate = uiState.nextDueDate,
        onNextDueDateChange = viewModel::onNextDueDateChange,
        cashAmount = uiState.cashAmount,
        onCashChange = viewModel::onCashChange,
        onlineAmount = uiState.onlineAmount,
        onOnlineChange = viewModel::onOnlineChange,
        totalPaid = uiState.totalPaid,
        cost = uiState.cost,
        onCostChange = viewModel::onCostChange,
        withFees = uiState.withFees,
        onFeesToggle = viewModel::onFeesToggle,
        doctorsAcc = uiState.doctorsAcc,
        onAccToggle = viewModel::onAccToggle,
        isLoading = uiState.isLoading,
        onSave = {
            if (VaccinationValidator.validateForm(context, uiState.patientId, uiState.selectedVaccines)) {
                val user = FirebaseAuth.getInstance().currentUser?.email ?: "Unknown"
                val v = VaccinationValidator.createVaccination(vaccinationId, uiState.patientId, uiState.selectedVaccines, uiState.nextBrandSearch, uiState.dateGiven, uiState.nextDueDate, uiState.cost, uiState.cashAmount, uiState.onlineAmount, uiState.totalPaid, uiState.withFees, uiState.doctorsAcc, uiState.batchNumbers, uiState.expiryDates, user)
                viewModel.saveVaccination(v, vaccinationId == null, uiState.selectedVaccineIds, uiState.selectedBatchIds) {}
            }
        },
        onSaveAndDownload = {
            val patient = allPatients.find { it.id == uiState.patientId }
            if (VaccinationValidator.validateForm(context, uiState.patientId, uiState.selectedVaccines) && patient != null) {
                val user = FirebaseAuth.getInstance().currentUser?.email ?: "Unknown"
                val v = VaccinationValidator.createVaccination(vaccinationId, uiState.patientId, uiState.selectedVaccines, uiState.nextBrandSearch, uiState.dateGiven, uiState.nextDueDate, uiState.cost, uiState.cashAmount, uiState.onlineAmount, uiState.totalPaid, uiState.withFees, uiState.doctorsAcc, uiState.batchNumbers, uiState.expiryDates, user)
                viewModel.saveVaccination(v, vaccinationId == null, uiState.selectedVaccineIds, uiState.selectedBatchIds) {
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
