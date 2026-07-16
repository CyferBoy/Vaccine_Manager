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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.clinic.neochild.domain.model.Vaccine
import com.clinic.neochild.core.ui.components.*
import com.clinic.neochild.core.ui.theme.NeoChildTheme
import com.clinic.neochild.data.mapper.FirestoreMappers
import com.clinic.neochild.core.utils.PatientUtils.formatDateForDisplay
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

@Composable
fun AddVaccineStockScreen(
    vaccineId: String? = null, 
    onBack: () -> Unit = {}
) {
    // Form State - using rememberSaveable
    var vaccineType by rememberSaveable { mutableStateOf("") }
    var brandName by rememberSaveable { mutableStateOf("") }
    var companyName by rememberSaveable { mutableStateOf("") }
    var stock by rememberSaveable { mutableStateOf("") }
    var batchNumber by rememberSaveable { mutableStateOf("") }
    var expiryDate by rememberSaveable { mutableStateOf("") }
    var mrp by rememberSaveable { mutableStateOf("") }
    var netRate by rememberSaveable { mutableStateOf("") }
    
    var allVaccines by remember { mutableStateOf<List<Vaccine>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    
    LaunchedEffect(Unit) {
        db.collection("inventory").get().addOnSuccessListener { result ->
            allVaccines = result.documents.mapNotNull { FirestoreMappers.toVaccine(it) }
        }
    }
    
    LaunchedEffect(vaccineId) {
        if (vaccineId != null) {
            isLoading = true
            db.collection("inventory").document(vaccineId).get().addOnSuccessListener { doc ->
                FirestoreMappers.toVaccine(doc)?.let { v ->
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
            }.addOnFailureListener { isLoading = false }
        }
    }

    AddVaccineStockContent(
        isEdit = vaccineId != null,
        onBack = onBack,
        vaccineType = vaccineType,
        onTypeChange = { vaccineType = it },
        brandName = brandName,
        onBrandChange = { brandName = it },
        companyName = companyName,
        onCompanyChange = { companyName = it },
        stock = stock,
        onStockChange = { stock = it },
        batchNumber = batchNumber,
        onBatchChange = { batchNumber = it },
        expiryDate = expiryDate,
        onExpiryChange = { expiryDate = it },
        mrp = mrp,
        onMrpChange = { mrp = it },
        netRate = netRate,
        onNetRateChange = { netRate = it },
        allVaccines = allVaccines,
        isLoading = isLoading,
        onDeleteRequest = { showDeleteDialog = true },
        onSave = {
            if (vaccineType.isBlank() || brandName.isBlank() || stock.isBlank()) {
                Toast.makeText(context, "Type, Brand and Quantity are required", Toast.LENGTH_SHORT).show()
                return@AddVaccineStockContent
            }
            isLoading = true
            val stockInt = stock.toIntOrNull() ?: 0
            val mrpDouble = mrp.toDoubleOrNull() ?: 0.0
            val netRateDouble = netRate.toDoubleOrNull() ?: 0.0
            
            val existingMatch = allVaccines.find { 
                it.brandName.equals(brandName, true) && it.batchNumber.equals(batchNumber, true) 
            }
            
            val docRef = if (vaccineId != null) db.collection("inventory").document(vaccineId)
                         else if (existingMatch != null) db.collection("inventory").document(existingMatch.id)
                         else db.collection("inventory").document()

            val vaccine = Vaccine(docRef.id, vaccineType, brandName, companyName, stockInt, batchNumber, expiryDate, mrpDouble, netRateDouble)
            docRef.set(vaccine).addOnSuccessListener {
                isLoading = false
                Toast.makeText(context, "Inventory updated", Toast.LENGTH_SHORT).show()
                onBack()
            }.addOnFailureListener {
                isLoading = false
                Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    )

    if (showDeleteDialog && vaccineId != null) {
        DeleteConfirmationDialog(
            show = true,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                isLoading = true
                db.collection("inventory").document(vaccineId).delete().addOnSuccessListener {
                    isLoading = false
                    Toast.makeText(context, "Batch deleted", Toast.LENGTH_SHORT).show()
                    onBack()
                }.addOnFailureListener {
                    isLoading = false
                    Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                }
            },
            title = "Delete Batch",
            message = "Are you sure you want to delete this batch?"
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddVaccineStockContent(
    isEdit: Boolean,
    onBack: () -> Unit,
    vaccineType: String,
    onTypeChange: (String) -> Unit,
    brandName: String,
    onBrandChange: (String) -> Unit,
    companyName: String,
    onCompanyChange: (String) -> Unit,
    stock: String,
    onStockChange: (String) -> Unit,
    batchNumber: String,
    onBatchChange: (String) -> Unit,
    expiryDate: String,
    onExpiryChange: (String) -> Unit,
    mrp: String,
    onMrpChange: (String) -> Unit,
    netRate: String,
    onNetRateChange: (String) -> Unit,
    allVaccines: List<Vaccine>,
    isLoading: Boolean,
    onDeleteRequest: () -> Unit,
    onSave: () -> Unit
) {
    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(if (isEdit) "Edit Vaccine" else "Add Vaccine") },
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Autocomplete fields
                VaccineTypeSelector(vaccineType, onTypeChange, allVaccines)
                BrandNameSelector(brandName, vaccineType, onBrandChange, allVaccines) { existing ->
                    onCompanyChange(existing.companyName)
                    onMrpChange(existing.mrp.toString())
                    onNetRateChange(existing.netRate.toString())
                }

                StandardTextField(value = companyName, onValueChange = onCompanyChange, label = "Company Name")
                StandardTextField(value = batchNumber, onValueChange = onBatchChange, label = "Batch Number")
                StandardTextField(value = stock, onValueChange = onStockChange, label = "Quantity*", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StandardTextField(value = mrp, onValueChange = onMrpChange, label = "MRP", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    StandardTextField(value = netRate, onValueChange = onNetRateChange, label = "Net Rate", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }

                DateDropdownPicker(label = "Expiry Date", currentDate = expiryDate, onDateSelected = onExpiryChange, modifier = Modifier.fillMaxWidth())

                Spacer(modifier = Modifier.height(16.dp))

                StandardButton(onClick = onSave, isLoading = isLoading) {
                    Text(if (isEdit) "Update Quantity" else "Add to Inventory")
                }

                if (isEdit) {
                    DeleteButton(onDeleteRequest)
                }
            }
        }
    }
}

@Composable
private fun VaccineTypeSelector(value: String, onValueChange: (String) -> Unit, allVaccines: List<Vaccine>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val types = remember(allVaccines) { allVaccines.map { it.type }.distinct().sorted() }
    
    StandardAutoCompleteField(
        value = value,
        onValueChange = onValueChange,
        label = "Vaccine Type*",
        placeholder = "Select or enter type",
        expanded = expanded && types.isNotEmpty(),
        onExpandedChange = { expanded = it },
        dropdownContent = {
            types.forEach { type ->
                DropdownMenuItem(text = { Text(type) }, onClick = { onValueChange(type); expanded = false })
            }
        }
    )
}

@Composable
private fun BrandNameSelector(value: String, type: String, onValueChange: (String) -> Unit, allVaccines: List<Vaccine>, onAutoFill: (Vaccine) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val brands = remember(type, allVaccines) {
        allVaccines.filter { it.type.equals(type, true) }.map { it.brandName }.distinct().sorted()
    }
    
    StandardAutoCompleteField(
        value = value,
        onValueChange = onValueChange,
        label = "Brand Name*",
        placeholder = "Select or enter brand",
        expanded = expanded && brands.isNotEmpty(),
        onExpandedChange = { expanded = it },
        dropdownContent = {
            brands.forEach { brand ->
                DropdownMenuItem(text = { Text(brand) }, onClick = {
                    onValueChange(brand)
                    expanded = false
                    allVaccines.find { it.brandName.equals(brand, true) }?.let { onAutoFill(it) }
                })
            }
        }
    )
}

@Composable
private fun DeleteButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
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

@Preview(showBackground = true)
@Composable
private fun AddVaccineStockPreview() {
    NeoChildTheme {
        AddVaccineStockContent(
            isEdit = false,
            onBack = {},
            vaccineType = "BCG",
            onTypeChange = {},
            brandName = "SII BCG",
            onBrandChange = {},
            companyName = "SII",
            onCompanyChange = {},
            stock = "10",
            onStockChange = {},
            batchNumber = "B123",
            onBatchChange = {},
            expiryDate = "1 Jan 2025",
            onExpiryChange = {},
            mrp = "500",
            onMrpChange = {},
            netRate = "400",
            onNetRateChange = {},
            allVaccines = emptyList(),
            isLoading = false,
            onDeleteRequest = {},
            onSave = {}
        )
    }
}
