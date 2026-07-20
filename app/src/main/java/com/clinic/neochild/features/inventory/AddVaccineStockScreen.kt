package com.clinic.neochild.features.inventory

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.clinic.neochild.domain.model.Vaccine
import com.clinic.neochild.core.ui.*
import com.clinic.neochild.core.designsystem.NeoChildTheme

@Composable
fun AddVaccineStockScreen(
    batchId: String? = null,
    onBack: () -> Unit = {},
    viewModel: AddVaccineStockViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val editingBatch by viewModel.editingBatch.collectAsState()
    
    // Form State - using rememberSaveable
    var vaccineType by rememberSaveable { mutableStateOf("") }
    var brandName by rememberSaveable { mutableStateOf("") }
    var companyName by rememberSaveable { mutableStateOf("") }
    var stock by rememberSaveable { mutableStateOf("") }
    var batchNumber by rememberSaveable { mutableStateOf("") }
    var expiryDate by rememberSaveable { mutableStateOf("") }
    var mrp by rememberSaveable { mutableStateOf("") }
    var netRate by rememberSaveable { mutableStateOf("") }
    
    val context = LocalContext.current

    LaunchedEffect(batchId) {
        if (batchId != null) {
            viewModel.loadBatch(batchId)
        }
    }

    LaunchedEffect(editingBatch, uiState.allVaccines) {
        editingBatch?.let { b ->
            val def = uiState.allVaccines.find { it.id == b.vaccineId }
            vaccineType = def?.type ?: ""
            brandName = def?.brandName ?: ""
            companyName = b.manufacturer
            stock = b.remainingQuantity.toString()
            batchNumber = b.batchNumber
            expiryDate = b.expiryDate
            mrp = b.sellingPrice.toString()
            netRate = b.purchaseCost.toString()
        }
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            Toast.makeText(context, "Inventory updated", Toast.LENGTH_SHORT).show()
            viewModel.resetSaveState()
            onBack()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.resetSaveState()
        }
    }

    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    AddVaccineStockContent(
        isEdit = batchId != null,
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
        allVaccines = uiState.allVaccines,
        isLoading = uiState.isLoading,
        onDeleteRequest = { showDeleteDialog = true },
        onSave = {
            if (vaccineType.isBlank() || brandName.isBlank() || stock.isBlank() || batchNumber.isBlank() || expiryDate.isBlank()) {
                Toast.makeText(context, "Please fill all required fields", Toast.LENGTH_SHORT).show()
                return@AddVaccineStockContent
            }
            
            viewModel.saveStock(
                editingBatchId = batchId,
                type = vaccineType,
                brandName = brandName,
                companyName = companyName,
                batchNumber = batchNumber,
                quantity = stock.toIntOrNull() ?: 0,
                expiryDate = expiryDate,
                mrp = mrp.toDoubleOrNull() ?: 0.0,
                netRate = netRate.toDoubleOrNull() ?: 0.0
            )
        }
    )

    if (showDeleteDialog && batchId != null) {
        DeleteConfirmationDialog(
            show = true,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                viewModel.deleteBatch(batchId)
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
