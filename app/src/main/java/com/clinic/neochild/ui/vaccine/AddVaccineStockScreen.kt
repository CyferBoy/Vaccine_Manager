package com.clinic.neochild.ui.vaccine

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.clinic.neochild.data.model.Vaccine
import com.clinic.neochild.ui.components.AppBackground
import com.clinic.neochild.ui.components.DateDropdownPicker
import com.clinic.neochild.ui.components.StandardAutoCompleteField
import com.clinic.neochild.ui.components.StandardButton
import com.clinic.neochild.ui.components.StandardTextField
import com.clinic.neochild.utils.FirestoreMappers
import com.clinic.neochild.utils.PatientUtils.formatDateForDisplay
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVaccineStockScreen(vaccineId: String? = null, onBack: () -> Unit = {}) {
    var vaccineType by remember { mutableStateOf("") }
    var brandName by remember { mutableStateOf("") }
    var companyName by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf("") }
    var batchNumber by remember { mutableStateOf("") }
    var expiryDate by remember { mutableStateOf("") }
    var mrp by remember { mutableStateOf("") }
    var netRate by remember { mutableStateOf("") }
    
    var allVaccines by remember { mutableStateOf<List<Vaccine>>(emptyList()) }
    var typeExpanded by remember { mutableStateOf(false) }
    var brandExpanded by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(value = false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    
    // Fetch all vaccines for dropdowns and lookup
    LaunchedEffect(Unit) {
        db.collection("inventory").get()
            .addOnSuccessListener { result ->
                allVaccines = result.documents.mapNotNull { FirestoreMappers.toVaccine(it) }
            }
    }
    
    LaunchedEffect(vaccineId) {
        if (vaccineId != null) {
            isLoading = true
            db.collection("inventory").document(vaccineId).get()
                .addOnSuccessListener { doc ->
                    val v = FirestoreMappers.toVaccine(doc)
                    if (v != null) {
                        vaccineType = v.type
                        brandName = v.brandName
                        companyName = v.companyName
                        stock = v.stock.toString()
                        batchNumber = v.batchNumber
                        expiryDate = formatDateForDisplay(v.expiryDate)
                        mrp = v.mrp.toString()
                        netRate = v.netRate.toString()
                    }
                    isLoading = false
                }
                .addOnFailureListener { isLoading = false }
        }
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(if (vaccineId == null) "Add Vaccine" else "Edit Vaccine") },
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
                // Vaccine Type Dropdown
                val types = allVaccines.map { it.type }.distinct().sorted()
                StandardAutoCompleteField(
                    value = vaccineType,
                    onValueChange = { vaccineType = it },
                    label = "Vaccine Type*",
                    placeholder = "Select or enter type",
                    expanded = typeExpanded && types.isNotEmpty(),
                    onExpandedChange = { typeExpanded = it },
                    dropdownContent = {
                        types.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = {
                                    vaccineType = type
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Brand Name Dropdown
                val brands = allVaccines
                    .filter { it.type.equals(vaccineType, ignoreCase = true) }
                    .map { it.brandName }
                    .distinct()
                    .sorted()
                    
                StandardAutoCompleteField(
                    value = brandName,
                    onValueChange = { brandName = it },
                    label = "Brand Name*",
                    placeholder = "Select or enter brand",
                    expanded = brandExpanded && brands.isNotEmpty(),
                    onExpandedChange = { brandExpanded = it },
                    dropdownContent = {
                        brands.forEach { brand ->
                            DropdownMenuItem(
                                text = { Text(brand) },
                                onClick = {
                                    brandName = brand
                                    brandExpanded = false
                                    // Auto-fill details from existing brand data
                                    allVaccines.find { it.brandName.equals(brand, ignoreCase = true) }?.let { existing ->
                                        companyName = existing.companyName
                                        mrp = existing.mrp.toString()
                                        netRate = existing.netRate.toString()
                                    }
                                }
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                StandardTextField(
                    value = companyName,
                    onValueChange = { companyName = it },
                    label = "Brand Company Name",
                    placeholder = "e.g. Serum Institute"
                )

                Spacer(modifier = Modifier.height(12.dp))

                StandardTextField(
                    value = batchNumber,
                    onValueChange = { batchNumber = it },
                    label = "Batch Number",
                    placeholder = "e.g. BT12345"
                )

                Spacer(modifier = Modifier.height(12.dp))

                StandardTextField(
                    value = stock,
                    onValueChange = { stock = it },
                    label = "Quantity*",
                    placeholder = "Enter number of doses",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(modifier = Modifier.height(12.dp))

                StandardTextField(
                    value = mrp,
                    onValueChange = { mrp = it },
                    label = "MRP",
                    placeholder = "0.00",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(modifier = Modifier.height(12.dp))

                StandardTextField(
                    value = netRate,
                    onValueChange = { netRate = it },
                    label = "Net Rate",
                    placeholder = "0.00",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(modifier = Modifier.height(16.dp))

                DateDropdownPicker(
                    label = "Expiry Date",
                    currentDate = expiryDate,
                    onDateSelected = { expiryDate = it },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(32.dp))

                StandardButton(
                    onClick = {
                        if (vaccineType.isBlank() || brandName.isBlank() || stock.isBlank()) {
                            Toast.makeText(context, "Type, Brand and Quantity are required", Toast.LENGTH_SHORT).show()
                            return@StandardButton
                        }

                        val stockInt = stock.toIntOrNull() ?: 0
                        val mrpDouble = mrp.toDoubleOrNull() ?: 0.0
                        val netRateDouble = netRate.toDoubleOrNull() ?: 0.0
                        
                        isLoading = true

                        // Check for existing records with same brand and batch
                        val existingMatch = allVaccines.find { 
                            it.brandName.equals(brandName, ignoreCase = true) && 
                            it.batchNumber.equals(batchNumber, ignoreCase = true) 
                        }

                        if (vaccineId == null && existingMatch != null) {
                            // Check if everything is exactly the same
                            val isIdentical = existingMatch.type.equals(vaccineType, ignoreCase = true) &&
                                              existingMatch.companyName.equals(companyName, ignoreCase = true) &&
                                              existingMatch.expiryDate == expiryDate &&
                                              existingMatch.mrp == mrpDouble &&
                                              existingMatch.netRate == netRateDouble

                            if (isIdentical) {
                                isLoading = false
                                Toast.makeText(context, "Data already exists", Toast.LENGTH_SHORT).show()
                                return@StandardButton
                            }
                        }

                        val docRef = if (vaccineId != null) {
                            db.collection("inventory").document(vaccineId)
                        } else if (existingMatch != null) {
                            // Batch same and something changed -> Update existing record
                            db.collection("inventory").document(existingMatch.id)
                        } else {
                            // New record
                            db.collection("inventory").document()
                        }
                        
                        val vaccine = Vaccine(
                            id = docRef.id,
                            type = vaccineType,
                            brandName = brandName,
                            companyName = companyName,
                            stock = stockInt, 
                            batchNumber = batchNumber,
                            expiryDate = expiryDate,
                            mrp = mrpDouble,
                            netRate = netRateDouble
                        )

                        docRef.set(vaccine)
                            .addOnSuccessListener {
                                isLoading = false
                                val msg = if (vaccineId != null) "Vaccine updated" 
                                          else if (existingMatch != null) "Batch details updated"
                                          else "Added to inventory"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                onBack()
                            }
                            .addOnFailureListener { e ->
                                isLoading = false
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    isLoading = isLoading
                ) {
                    Text(if (vaccineId == null) "Add to Inventory" else "Update Quantity", style = MaterialTheme.typography.titleMedium)
                }

                if (vaccineId != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete Record")
                    }
                }
            }
        }
    }

    if (showDeleteDialog && vaccineId != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Vaccine Batch") },
            text = { Text("Are you sure you want to delete this batch ($batchNumber)? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    isLoading = true
                    db.collection("inventory").document(vaccineId).delete()
                        .addOnSuccessListener {
                            isLoading = false
                            Toast.makeText(context, "Batch deleted", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                        .addOnFailureListener { e ->
                            isLoading = false
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
