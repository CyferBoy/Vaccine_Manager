package com.clinic.neochild.ui.vaccine

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clinic.neochild.data.model.Vaccine
import com.clinic.neochild.data.model.WasteRecord
import com.clinic.neochild.ui.components.AppBackground
import com.clinic.neochild.ui.components.DateDropdownPicker
import com.clinic.neochild.ui.components.StandardAutoCompleteField
import com.clinic.neochild.ui.components.StandardButton
import com.clinic.neochild.ui.components.StandardTextField
import com.clinic.neochild.utils.Constants
import com.clinic.neochild.utils.FirestoreMappers
import com.clinic.neochild.utils.PatientUtils.formatDateForDisplay
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WasteScreen(onBack: () -> Unit) {
    var wasteRecords by remember { mutableStateOf<List<WasteRecord>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddDialog by remember { mutableStateOf(false) }

    val db = FirebaseFirestore.getInstance()

    DisposableEffect(Unit) {
        val listener = db.collection("waste")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    isLoading = false
                    return@addSnapshotListener
                }
                wasteRecords = snapshot?.documents?.mapNotNull { FirestoreMappers.toWasteRecord(it) }
                    ?.sortedByDescending { it.dateWasted } ?: emptyList()
                isLoading = false
            }

        onDispose {
            listener.remove()
        }
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                TopAppBar(
                    title = { Text("Waste Records") },
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
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Waste")
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                if (isLoading && wasteRecords.isEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (wasteRecords.isEmpty()) {
                    Text("No waste records found", modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 88.dp, top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(wasteRecords) { record ->
                            WasteItemCard(record, onDelete = {
                                db.collection("waste").document(record.id).delete()
                            })
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddWasteDialog(
            onDismiss = { showAddDialog = false },
            onSave = { /* No longer needed as listener handles it */ }
        )
    }
}

@Composable
fun WasteItemCard(record: WasteRecord, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(record.brandName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Batch: ${record.batchNumber}", style = MaterialTheme.typography.bodySmall)
                Text("Reason: ${record.reason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Date: ${formatDateForDisplay(record.dateWasted)}", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWasteDialog(onDismiss: () -> Unit, onSave: () -> Unit) {
    var selectedBrand by remember { mutableStateOf("") }
    var selectedVaccineId by remember { mutableStateOf("") }
    var batchNumber by remember { mutableStateOf("") }
    var expiryDate by remember { mutableStateOf("") }
    
    val today = SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(Date())
    var dateWasted by remember { mutableStateOf(today) }
    var reason by remember { mutableStateOf("Administration Waste") }
    
    var isLoading by remember { mutableStateOf(false) }
    var inventory by remember { mutableStateOf<List<Vaccine>>(emptyList()) }
    var expandedBrand by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()

    LaunchedEffect(Unit) {
        db.collection("inventory").get().addOnSuccessListener { result ->
            inventory = result.documents.mapNotNull { FirestoreMappers.toVaccine(it) }
        }
    }

    val availableBrands = remember(selectedBrand, inventory) {
        inventory.filter { 
            it.brandName.contains(selectedBrand, ignoreCase = true) ||
            it.type.contains(selectedBrand, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record New Waste") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StandardAutoCompleteField(
                    value = selectedBrand,
                    onValueChange = { 
                        selectedBrand = it
                        expandedBrand = true 
                    },
                    label = "Select Vaccine*",
                    placeholder = "Search inventory...",
                    expanded = expandedBrand && availableBrands.isNotEmpty(),
                    onExpandedChange = { expandedBrand = it },
                    dropdownContent = {
                        availableBrands.forEach { v ->
                            DropdownMenuItem(
                                text = { Text("${v.brandName} (Batch: ${v.batchNumber})") },
                                onClick = { 
                                    selectedBrand = v.brandName
                                    selectedVaccineId = v.id
                                    batchNumber = v.batchNumber
                                    expiryDate = v.expiryDate
                                    expandedBrand = false 
                                }
                            )
                        }
                    }
                )

                Row(modifier = Modifier.fillMaxWidth()) {
                    StandardTextField(
                        value = batchNumber,
                        onValueChange = { batchNumber = it },
                        label = "Batch",
                        readOnly = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    StandardTextField(
                        value = expiryDate,
                        onValueChange = { expiryDate = it },
                        label = "Exp",
                        readOnly = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                DateDropdownPicker(
                    label = "Date Wasted*",
                    currentDate = dateWasted,
                    onDateSelected = { dateWasted = it },
                    modifier = Modifier.fillMaxWidth()
                )

                StandardTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = "Reason",
                    placeholder = "e.g. Broken vial"
                )
            }
        },
        confirmButton = {
            StandardButton(
                onClick = {
                    if (selectedVaccineId.isEmpty()) {
                        Toast.makeText(context, "Please select a vaccine", Toast.LENGTH_SHORT).show()
                        return@StandardButton
                    }
                    isLoading = true
                    val wasteData = hashMapOf(
                        "vaccineId" to selectedVaccineId,
                        "brandName" to selectedBrand,
                        "batchNumber" to batchNumber,
                        "expiryDate" to expiryDate,
                        "dateWasted" to dateWasted,
                        "reason" to reason,
                        "quantity" to 1
                    )
                    db.collection("waste").add(wasteData).addOnSuccessListener {
                        val currentStock = inventory.find { it.id == selectedVaccineId }?.stock ?: 0
                        if (currentStock > 0) {
                            db.collection("inventory").document(selectedVaccineId)
                                .update("stock", currentStock - 1)
                                .addOnSuccessListener {
                                    Toast.makeText(context, "Waste recorded", Toast.LENGTH_SHORT).show()
                                    onSave()
                                    onDismiss()
                                }
                        } else {
                            onSave()
                            onDismiss()
                        }
                    }.addOnFailureListener { isLoading = false }
                },
                isLoading = isLoading
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
