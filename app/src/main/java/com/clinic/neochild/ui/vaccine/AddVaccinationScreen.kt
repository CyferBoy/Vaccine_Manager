package com.clinic.neochild.ui.vaccine

import android.widget.Toast
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clinic.neochild.data.model.Vaccination
import com.clinic.neochild.data.model.Vaccine
import com.clinic.neochild.ui.components.AppBackground
import com.clinic.neochild.ui.components.DateDropdownPicker
import com.clinic.neochild.ui.components.StandardAutoCompleteField
import com.clinic.neochild.ui.components.StandardButton
import com.clinic.neochild.ui.components.StandardTextField
import com.clinic.neochild.ui.viewmodel.PatientViewModel
import com.clinic.neochild.utils.Constants
import com.clinic.neochild.utils.FirestoreMappers
import com.clinic.neochild.utils.PatientUtils.formatDateForDisplay
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVaccinationScreen(
    initialPatientId: String = "", 
    vaccinationId: String? = null,
    onBack: () -> Unit = {},
    viewModel: PatientViewModel = viewModel()
) {

    var patientId by remember { mutableStateOf(initialPatientId) }
    var selectedBrand by remember { mutableStateOf("") }
    var selectedVaccines by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedVaccineIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var batchNumbers by remember { mutableStateOf<List<String>>(emptyList()) }
    var expiryDates by remember { mutableStateOf<List<String>>(emptyList()) }
    
    var nextBrandSearch by remember { mutableStateOf("") }
    
    val today = SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(Date())
    var dateGiven by remember { mutableStateOf(today) }
    var nextDueDate by remember { mutableStateOf("") }
    
    var cost by remember { mutableStateOf("") }
    var cashAmount by remember { mutableStateOf("") }
    var onlineAmount by remember { mutableStateOf("") }
    var withFees by remember { mutableStateOf(false) }
    var doctorsAcc by remember { mutableStateOf(false) }
    
    var isLoading by remember { mutableStateOf(false) }
    var inventory by remember { mutableStateOf<List<Vaccine>>(emptyList()) }
    
    var expandedBrand by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    
    LaunchedEffect(vaccinationId) {
        if (vaccinationId != null) {
            isLoading = true
            db.collection("vaccinations").document(vaccinationId).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        patientId = doc.getString("patientId") ?: ""
                        @Suppress("UNCHECKED_CAST")
                        selectedVaccines = doc.get("vaccineNames") as? List<String> ?: listOf(doc.getString("vaccineName") ?: "")
                        batchNumbers = (doc.get("batchNumbers") as? List<String>) ?: listOf(doc.getString("batchNumber") ?: "")
                        expiryDates = (doc.get("expiryDates") as? List<String>) ?: listOf(doc.getString("expiryDate") ?: "")
                        nextBrandSearch = (doc.get("nxtVaccineNames") as? List<String> ?: listOf(doc.getString("nxtVaccineName") ?: "").filter { it.isNotEmpty() }).joinToString(", ")
                    }
                    isLoading = false
                }
                .addOnFailureListener { isLoading = false }
        }
    }

    val totalPaid = (cashAmount.toDoubleOrNull() ?: 0.0) + (onlineAmount.toDoubleOrNull() ?: 0.0)

    LaunchedEffect(totalPaid) {
        if (totalPaid > 0) {
            cost = if (totalPaid % 1.0 == 0.0) totalPaid.toInt().toString() else totalPaid.toString()
        }
    }

    LaunchedEffect(Unit) {
        db.collection("inventory").get().addOnSuccessListener { result ->
            inventory = result.documents.mapNotNull { doc ->
                FirestoreMappers.toVaccine(doc)
            }
        }
    }

    val availableBrands = remember(selectedBrand, inventory) {
        inventory.asSequence().filter { 
            it.brandName.contains(selectedBrand, ignoreCase = true) ||
            it.type.contains(selectedBrand, ignoreCase = true)
        }.toList()
    }

    val suggestedTypes = remember(selectedBrand) {
        if (selectedBrand.isBlank()) emptyList()
        else Constants.COMMON_VACCINES.asSequence().filter { it.contains(selectedBrand, ignoreCase = true) }.toList()
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(if (vaccinationId == null) "Add Vaccination" else "Edit Vaccination") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary, // Solid header color
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
                    .imePadding() // Ensures content moves above keyboard
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {

                StandardTextField(
                    value = patientId,
                    onValueChange = { patientId = it },
                    label = "Patient ID*",
                    placeholder = "Enter patient ID (e.g., P001)",
                    enabled = initialPatientId.isEmpty()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Current Vaccine Selection
                StandardAutoCompleteField(
                    value = selectedBrand,
                    onValueChange = { 
                        selectedBrand = it
                        expandedBrand = true 
                    },
                    label = "Select Vaccine*",
                    placeholder = "Search by brand or type...",
                    expanded = expandedBrand && (availableBrands.isNotEmpty() || suggestedTypes.isNotEmpty() || selectedBrand.isNotBlank()),
                    onExpandedChange = { expandedBrand = it },
                    dropdownContent = {
                        if (availableBrands.isNotEmpty()) {
                            Text("Inventory Items", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(8.dp), color = MaterialTheme.colorScheme.primary)
                            availableBrands.forEach { v ->
                                val batchInfo = "Batch: ${v.batchNumber}"
                                DropdownMenuItem(
                                    text = { Text("${v.brandName} - $batchInfo (Stock: ${v.stock})") },
                                    onClick = { 
                                        selectedBrand = "" 
                                        if (!selectedVaccines.contains(v.brandName)) {
                                            selectedVaccines = selectedVaccines + v.brandName
                                            batchNumbers = batchNumbers + v.batchNumber
                                            expiryDates = expiryDates + v.expiryDate
                                        }
                                        if (!selectedVaccineIds.contains(v.id)) {
                                            selectedVaccineIds = selectedVaccineIds + v.id
                                        }
                                        
                                        expandedBrand = false 
                                    }
                                )
                            }
                        }
                        
                        if (suggestedTypes.isNotEmpty()) {
                            if (availableBrands.isNotEmpty()) HorizontalDivider()
                            Text("Suggestions", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            suggestedTypes.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type) },
                                    onClick = { 
                                        selectedBrand = "" 
                                        if (!selectedVaccines.contains(type)) {
                                            selectedVaccines = selectedVaccines + type
                                        }
                                        expandedBrand = false 
                                    }
                                )
                            }
                        }

                        if (selectedBrand.isNotBlank() && !suggestedTypes.contains(selectedBrand) && !availableBrands.any { it.brandName.equals(selectedBrand, true) }) {
                            DropdownMenuItem(
                                text = { Text("Add Custom: \"$selectedBrand\"") },
                                onClick = { 
                                    if (!selectedVaccines.contains(selectedBrand)) {
                                        selectedVaccines = selectedVaccines + selectedBrand
                                        batchNumbers = batchNumbers + ""
                                        expiryDates = expiryDates + ""
                                    }
                                    selectedBrand = ""
                                    expandedBrand = false
                                }
                            )
                        }
                    }
                )

                if (selectedVaccines.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    selectedVaccines.forEach { vaccine ->
                        Surface(
                            modifier = Modifier.padding(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = vaccine, style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = { 
                                        val index = selectedVaccines.indexOf(vaccine)
                                        if (index != -1) {
                                            selectedVaccines = selectedVaccines.toMutableList().apply { removeAt(index) }
                                            batchNumbers = batchNumbers.toMutableList().apply { removeAt(index) }
                                            expiryDates = expiryDates.toMutableList().apply { removeAt(index) }
                                        }
                                        
                                        // Remove corresponding ID if we can find it
                                        val vaccineObj = inventory.find { it.brandName == vaccine }
                                        if (vaccineObj != null) {
                                            selectedVaccineIds = selectedVaccineIds - vaccineObj.id
                                        }
                                    },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Next Vaccine Name
                StandardTextField(
                    value = nextBrandSearch,
                    onValueChange = { nextBrandSearch = it },
                    label = "Next Vaccine",
                    placeholder = "Enter next vaccine name (e.g. DPT Booster)"
                )

                Spacer(modifier = Modifier.height(12.dp))

                DateDropdownPicker(
                    label = "Date Given*",
                    currentDate = dateGiven,
                    onDateSelected = { dateGiven = it },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                DateDropdownPicker(
                    label = "Next Due Date",
                    currentDate = nextDueDate,
                    onDateSelected = { nextDueDate = it },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Payment Mode", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp))
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    StandardTextField(
                        value = cashAmount,
                        onValueChange = { cashAmount = it },
                        label = "Cash",
                        placeholder = "Enter cash amount",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    StandardTextField(
                        value = onlineAmount,
                        onValueChange = { onlineAmount = it },
                        label = "Online",
                        placeholder = "Enter online amount",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StandardTextField(
                        value = if (totalPaid % 1.0 == 0.0) totalPaid.toInt().toString() else totalPaid.toString(),
                        onValueChange = { },
                        label = "Total",
                        readOnly = true,
                        modifier = Modifier.weight(1f)
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(start = 8.dp, top = 20.dp)
                    ) {
                        Checkbox(
                            checked = withFees,
                            onCheckedChange = { withFees = it }
                        )
                        Text(
                            text = "With Fees",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StandardTextField(
                        value = cost,
                        onValueChange = { cost = it },
                        label = "Cost / Price",
                        placeholder = "Enter total cost (e.g. 500)",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(start = 8.dp, top = 20.dp)
                    ) {
                        Checkbox(
                            checked = doctorsAcc,
                            onCheckedChange = { doctorsAcc = it }
                        )
                        Text(
                            text = "Doctors Acc",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                val currentPatient = viewModel.allPatients.collectAsState().value.find { it.id == patientId }

                Row(modifier = Modifier.fillMaxWidth()) {
                    StandardButton(
                        onClick = {
                            if (patientId.isBlank() || selectedVaccines.isEmpty()) {
                                Toast.makeText(context, "Patient ID and at least one Vaccine are required", Toast.LENGTH_SHORT).show()
                                return@StandardButton
                            }

                            isLoading = true
                            
                            val vaccination = Vaccination(
                                id = vaccinationId ?: UUID.randomUUID().toString(),
                                patientId = patientId,
                                vaccineNames = selectedVaccines,
                                nxtVaccineNames = nextBrandSearch.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                                dateGiven = dateGiven,
                                nextDueDate = nextDueDate,
                                cost = cost.toDoubleOrNull() ?: 0.0,
                                cashAmount = cashAmount.toDoubleOrNull() ?: 0.0,
                                onlineAmount = onlineAmount.toDoubleOrNull() ?: 0.0,
                                totalPaid = totalPaid,
                                withFees = withFees,
                                doctorsAcc = doctorsAcc,
                                batchNumbers = batchNumbers,
                                expiryDates = expiryDates,
                                batchNumber = batchNumbers.firstOrNull() ?: "",
                                expiryDate = expiryDates.firstOrNull() ?: ""
                            )

                            viewModel.saveVaccination(vaccination) {
                                handlePostSave(vaccination, vaccinationId == null, db, inventory, selectedVaccineIds, patientId)
                                isLoading = false
                                Toast.makeText(context, if (vaccinationId == null) "Vaccination Saved" else "Vaccination Updated", Toast.LENGTH_SHORT).show()
                                onBack()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        isLoading = isLoading
                    ) {
                        Text(if (vaccinationId == null) "Save" else "Update", style = MaterialTheme.typography.titleMedium)
                    }

                    if (vaccinationId == null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        StandardButton(
                            onClick = {
                                if (patientId.isBlank() || selectedVaccines.isEmpty()) {
                                    Toast.makeText(context, "Patient ID and at least one Vaccine are required", Toast.LENGTH_SHORT).show()
                                    return@StandardButton
                                }
                                if (currentPatient == null) {
                                    Toast.makeText(context, "Patient not found. Cannot print receipt.", Toast.LENGTH_SHORT).show()
                                    return@StandardButton
                                }

                                isLoading = true
                                val vaccination = Vaccination(
                                    id = UUID.randomUUID().toString(),
                                    patientId = patientId,
                                    vaccineNames = selectedVaccines,
                                    nxtVaccineNames = nextBrandSearch.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                                    dateGiven = dateGiven,
                                    nextDueDate = nextDueDate,
                                    cost = cost.toDoubleOrNull() ?: 0.0,
                                    cashAmount = cashAmount.toDoubleOrNull() ?: 0.0,
                                    onlineAmount = onlineAmount.toDoubleOrNull() ?: 0.0,
                                    totalPaid = totalPaid,
                                    withFees = withFees,
                                    doctorsAcc = doctorsAcc,
                                    batchNumbers = batchNumbers,
                                    expiryDates = expiryDates,
                                    batchNumber = batchNumbers.firstOrNull() ?: "",
                                    expiryDate = expiryDates.firstOrNull() ?: ""
                                )

                                viewModel.saveVaccination(vaccination) {
                                    handlePostSave(vaccination, true, db, inventory, selectedVaccineIds, patientId)
                                    isLoading = false
                                    com.clinic.neochild.utils.ReceiptManager.downloadReceipt(context, currentPatient, vaccination)
                                    Toast.makeText(context, "Saved & Downloading Receipt", Toast.LENGTH_SHORT).show()
                                    onBack()
                                }
                            },
                            modifier = Modifier.weight(1.5f),
                            containerColor = MaterialTheme.colorScheme.secondary,
                            isLoading = isLoading
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save & Download", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}

private fun handlePostSave(
    vaccination: Vaccination,
    isNew: Boolean,
    db: FirebaseFirestore,
    inventory: List<Vaccine>,
    selectedVaccineIds: List<String>,
    patientId: String
) {
    if (isNew) {
        // Update stock
        selectedVaccineIds.forEach { id ->
            val selectedVaccine = inventory.find { it.id == id }
            if (selectedVaccine != null && selectedVaccine.stock > 0) {
                db.collection("inventory").document(id)
                    .update("stock", selectedVaccine.stock - 1)
            }
        }

        // Mark previous "due" records as done for this patient
        db.collection("vaccinations")
            .whereEqualTo("patientId", patientId)
            .whereEqualTo("isDone", false)
            .get()
            .addOnSuccessListener { result ->
                val batch = db.batch()
                for (document in result) {
                    if (document.id != vaccination.id) {
                        batch.update(document.reference, "isDone", true)
                    }
                }
                batch.commit()
            }
    }
}
