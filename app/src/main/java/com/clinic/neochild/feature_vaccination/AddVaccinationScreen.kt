package com.clinic.neochild.feature_vaccination

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Print
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
import kotlinx.coroutines.launch
import com.clinic.neochild.domain.model.Patient
import com.clinic.neochild.domain.model.Vaccination
import com.clinic.neochild.domain.model.Vaccine
import com.clinic.neochild.core.ui.components.*
import com.clinic.neochild.core.ui.theme.NeoChildTheme
import com.clinic.neochild.ui.viewmodel.PatientViewModel
import com.clinic.neochild.core.common.Constants
import com.clinic.neochild.utils.ReceiptManager
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
            if (validateForm(context, patientId, selectedVaccines)) {
                val user = FirebaseAuth.getInstance().currentUser?.email ?: "Unknown"
                val v = createVaccination(vaccinationId, patientId, selectedVaccines, nextBrandSearch, dateGiven, nextDueDate, cost, cashAmount, onlineAmount, totalPaid, withFees, doctorsAcc, batchNumbers, expiryDates, user)
                viewModel.saveVaccination(v, vaccinationId == null, selectedVaccineIds) {}
            }
        },
        onSaveAndDownload = {
            val patient = allPatients.find { it.id == patientId }
            if (validateForm(context, patientId, selectedVaccines) && patient != null) {
                val user = FirebaseAuth.getInstance().currentUser?.email ?: "Unknown"
                val v = createVaccination(vaccinationId, patientId, selectedVaccines, nextBrandSearch, dateGiven, nextDueDate, cost, cashAmount, onlineAmount, totalPaid, withFees, doctorsAcc, batchNumbers, expiryDates, user)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddVaccinationContent(
    isEdit: Boolean,
    onBack: () -> Unit,
    patientId: String,
    onPatientIdChange: (String) -> Unit,
    isPatientIdEnabled: Boolean,
    inventory: List<Vaccine>,
    selectedVaccines: List<String>,
    onVaccineSelected: (Vaccine) -> Unit,
    onCustomVaccineAdded: (String) -> Unit,
    onRemoveVaccine: (Int) -> Unit,
    nextBrandSearch: String,
    onNextBrandChange: (String) -> Unit,
    dateGiven: String,
    onDateGivenChange: (String) -> Unit,
    nextDueDate: String,
    onNextDueDateChange: (String) -> Unit,
    cashAmount: String,
    onCashChange: (String) -> Unit,
    onlineAmount: String,
    onOnlineChange: (String) -> Unit,
    totalPaid: Double,
    cost: String,
    onCostChange: (String) -> Unit,
    withFees: Boolean,
    onFeesToggle: (Boolean) -> Unit,
    doctorsAcc: Boolean,
    onAccToggle: (Boolean) -> Unit,
    isLoading: Boolean,
    onSave: () -> Unit,
    onSaveAndDownload: () -> Unit
) {
    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(if (isEdit) "Edit Vaccination" else "Add Vaccination") },
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
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .imePadding()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StandardTextField(
                    value = patientId,
                    onValueChange = onPatientIdChange,
                    label = "Patient ID*",
                    placeholder = "Enter patient ID",
                    enabled = isPatientIdEnabled
                )

                VaccineSelectionSection(
                    inventory = inventory,
                    selectedVaccines = selectedVaccines,
                    onVaccineSelected = onVaccineSelected,
                    onCustomVaccineAdded = onCustomVaccineAdded,
                    onRemoveVaccine = onRemoveVaccine
                )

                StandardTextField(
                    value = nextBrandSearch,
                    onValueChange = onNextBrandChange,
                    label = "Next Vaccine",
                    placeholder = "Enter next vaccine name"
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateDropdownPicker(label = "Date Given*", currentDate = dateGiven, onDateSelected = onDateGivenChange, modifier = Modifier.weight(1f))
                    DateDropdownPicker(label = "Next Due Date", currentDate = nextDueDate, onDateSelected = onNextDueDateChange, modifier = Modifier.weight(1f))
                }

                PaymentSection(
                    cash = cashAmount,
                    online = onlineAmount,
                    total = totalPaid,
                    cost = cost,
                    withFees = withFees,
                    doctorsAcc = doctorsAcc,
                    onCashChange = onCashChange,
                    onOnlineChange = onOnlineChange,
                    onCostChange = onCostChange,
                    onFeesToggle = onFeesToggle,
                    onAccToggle = onAccToggle
                )

                Spacer(modifier = Modifier.height(16.dp))

                ActionButtons(
                    isLoading = isLoading,
                    isEdit = isEdit,
                    onSave = onSave,
                    onSaveAndDownload = onSaveAndDownload
                )
            }
        }
    }
}

@Composable
private fun VaccineSelectionSection(
    inventory: List<Vaccine>,
    selectedVaccines: List<String>,
    onVaccineSelected: (Vaccine) -> Unit,
    onCustomVaccineAdded: (String) -> Unit,
    onRemoveVaccine: (Int) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(false) }

    val filteredInventory = remember(query, inventory) {
        inventory.filter { it.brandName.contains(query, true) || it.type.contains(query, true) }
    }
    val suggestedTypes = remember(query) {
        Constants.COMMON_VACCINES.filter { it.contains(query, true) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StandardAutoCompleteField(
            value = query,
            onValueChange = { query = it; expanded = true },
            label = "Select Vaccine*",
            placeholder = "Search inventory or suggestions...",
            expanded = expanded && query.isNotBlank(),
            onExpandedChange = { expanded = it },
            dropdownContent = {
                if (filteredInventory.isNotEmpty()) {
                    Text("Inventory", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(8.dp), color = MaterialTheme.colorScheme.primary)
                    filteredInventory.forEach { v ->
                        DropdownMenuItem(
                            text = { Text("${v.brandName} (Stock: ${v.stock})") },
                            onClick = { onVaccineSelected(v); query = ""; expanded = false }
                        )
                    }
                }
                if (suggestedTypes.isNotEmpty()) {
                    Text("Suggestions", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(8.dp))
                    suggestedTypes.forEach { type ->
                        DropdownMenuItem(text = { Text(type) }, onClick = { onCustomVaccineAdded(type); query = ""; expanded = false })
                    }
                }
                if (query.isNotBlank()) {
                    DropdownMenuItem(text = { Text("Add Custom: \"$query\"") }, onClick = { onCustomVaccineAdded(query); query = ""; expanded = false })
                }
            }
        )

        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            selectedVaccines.forEachIndexed { index, name ->
                InputChip(
                    selected = true,
                    onClick = { },
                    label = { Text(name) },
                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp).clickable { onRemoveVaccine(index) }) }
                )
            }
        }
    }
}

@Composable
private fun PaymentSection(
    cash: String, online: String, total: Double, cost: String, withFees: Boolean, doctorsAcc: Boolean,
    onCashChange: (String) -> Unit, onOnlineChange: (String) -> Unit, onCostChange: (String) -> Unit,
    onFeesToggle: (Boolean) -> Unit, onAccToggle: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Payment & Cost", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StandardTextField(value = cash, onValueChange = onCashChange, label = "Cash", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            StandardTextField(value = online, onValueChange = onOnlineChange, label = "Online", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val totalPaidDisplay = remember(total) { if (total % 1.0 == 0.0) total.toInt().toString() else total.toString() }
            StandardTextField(value = totalPaidDisplay, onValueChange = {}, label = "Total Paid", readOnly = true, modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Checkbox(checked = withFees, onCheckedChange = onFeesToggle)
                Text("With Fees", style = MaterialTheme.typography.labelSmall)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StandardTextField(value = cost, onValueChange = onCostChange, label = "Actual Cost", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Checkbox(checked = doctorsAcc, onCheckedChange = onAccToggle)
                Text("Dr. Acc", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ActionButtons(isLoading: Boolean, isEdit: Boolean, onSave: () -> Unit, onSaveAndDownload: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StandardButton(onClick = onSave, isLoading = isLoading, modifier = Modifier.weight(1f)) {
            Text(if (isEdit) "Update" else "Save")
        }
        if (!isEdit) {
            StandardButton(onClick = onSaveAndDownload, isLoading = isLoading, containerColor = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1.5f)) {
                Icon(Icons.Default.Print, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save & Download")
            }
        }
    }
}

private fun validateForm(context: android.content.Context, patientId: String, vaccines: List<String>): Boolean {
    if (patientId.isBlank() || vaccines.isEmpty()) {
        Toast.makeText(context, "Patient ID and at least one Vaccine are required", Toast.LENGTH_SHORT).show()
        return false
    }
    return true
}

private fun createVaccination(
    id: String?, patientId: String, vaccines: List<String>, nextVaccine: String, dateGiven: String, nextDue: String,
    cost: String, cash: String, online: String, total: Double, withFees: Boolean, doctorsAcc: Boolean,
    batches: List<String>, expiries: List<String>, performedBy: String = ""
) = Vaccination(
    id = id ?: UUID.randomUUID().toString(),
    patientId = patientId,
    vaccineNames = vaccines,
    nxtVaccineNames = nextVaccine.split(",").map { it.trim() }.filter { it.isNotEmpty() },
    dateGiven = dateGiven,
    nextDueDate = nextDue,
    cost = cost.toDoubleOrNull() ?: 0.0,
    cashAmount = cash.toDoubleOrNull() ?: 0.0,
    onlineAmount = online.toDoubleOrNull() ?: 0.0,
    totalPaid = total,
    withFees = withFees,
    doctorsAcc = doctorsAcc,
    batchNumbers = batches,
    expiryDates = expiries,
    performedBy = performedBy
)

@Preview(showBackground = true)
@Composable
private fun AddVaccinationPreview() {
    NeoChildTheme {
        AddVaccinationContent(
            isEdit = false,
            onBack = {},
            patientId = "P001",
            onPatientIdChange = {},
            isPatientIdEnabled = true,
            inventory = emptyList(),
            selectedVaccines = listOf("BCG", "HepB"),
            onVaccineSelected = {},
            onCustomVaccineAdded = {},
            onRemoveVaccine = {},
            nextBrandSearch = "",
            onNextBrandChange = {},
            dateGiven = "1 Jan 2024",
            onDateGivenChange = {},
            nextDueDate = "1 Feb 2024",
            onNextDueDateChange = {},
            cashAmount = "500",
            onCashChange = {},
            onlineAmount = "0",
            onOnlineChange = {},
            totalPaid = 500.0,
            cost = "500",
            onCostChange = {},
            withFees = false,
            onFeesToggle = {},
            doctorsAcc = false,
            onAccToggle = {},
            isLoading = false,
            onSave = {},
            onSaveAndDownload = {}
        )
    }
}
