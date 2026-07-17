package com.clinic.neochild.features.vaccination

import android.widget.Toast
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
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
                selectedVaccines = selectedVaccines + v.brandName
                batchNumbers = batchNumbers + v.batchNumber
                expiryDates = expiryDates + v.expiryDate
                selectedVaccineIds = selectedVaccineIds + v.id
            }
        },
        onCustomVaccineAdded = { name ->
            if (!selectedVaccines.contains(name)) {
                selectedVaccines = selectedVaccines + name
                batchNumbers = batchNumbers + ""
                expiryDates = expiryDates + ""
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
