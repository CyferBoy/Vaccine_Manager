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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.clinic.neochild.domain.model.InventoryItem
import com.clinic.neochild.core.ui.*

@Composable
fun AddVaccineStockScreen(
    batchId: String? = null,
    onBack: () -> Unit = {},
    viewModel: AddVaccineStockViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val editingBatch by viewModel.editingBatch.collectAsState()
    
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

    LaunchedEffect(editingBatch, uiState.allItems) {
        editingBatch?.let { b ->
            val item = uiState.allItems.find { it.id == b.vaccineId }
            vaccineType = item?.type ?: ""
            brandName = item?.brandName ?: ""
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

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                StandardTopAppBar(
                    title = if (batchId != null) "Edit Batch" else "Add Stock",
                    onBack = onBack
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
                VaccineTypeSelector(vaccineType, { vaccineType = it }, uiState.allItems)
                BrandNameSelector(brandName, vaccineType, { brandName = it }, uiState.allItems) { existing ->
                    companyName = existing.company
                    // For mrp/netRate we take from first batch if exists
                    existing.batches.firstOrNull()?.let {
                        mrp = it.sellingPrice.toString()
                        netRate = it.purchaseCost.toString()
                    }
                }

                StandardTextField(value = companyName, onValueChange = { companyName = it }, label = "Manufacturer*")
                StandardTextField(value = batchNumber, onValueChange = { batchNumber = it }, label = "Batch Number*")
                StandardTextField(value = stock, onValueChange = { stock = it }, label = "Quantity*", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StandardTextField(value = mrp, onValueChange = { mrp = it }, label = "MRP", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    StandardTextField(value = netRate, onValueChange = { netRate = it }, label = "Net Rate", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }

                DateDropdownPicker(label = "Expiry Date*", currentDate = expiryDate, onDateSelected = { expiryDate = it }, modifier = Modifier.fillMaxWidth())

                Spacer(modifier = Modifier.height(16.dp))

                StandardButton(
                    onClick = {
                        if (vaccineType.isBlank() || brandName.isBlank() || stock.isBlank() || batchNumber.isBlank() || expiryDate.isBlank()) {
                            Toast.makeText(context, "Please fill required fields", Toast.LENGTH_SHORT).show()
                            return@StandardButton
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
                    },
                    isLoading = uiState.isLoading
                ) {
                    Text(if (batchId != null) "Update Batch" else "Add to Inventory")
                }

                if (batchId != null) {
                    OutlinedButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Delete, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete Batch")
                    }
                }
            }
        }
    }

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

@Composable
private fun VaccineTypeSelector(value: String, onValueChange: (String) -> Unit, allItems: List<InventoryItem>) {
    var expanded by remember { mutableStateOf(false) }
    val types = remember(allItems) { allItems.map { it.type }.distinct().sorted() }
    
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
private fun BrandNameSelector(value: String, type: String, onValueChange: (String) -> Unit, allItems: List<InventoryItem>, onAutoFill: (InventoryItem) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val brands = remember(type, allItems) {
        allItems.filter { it.type.equals(type, true) }.map { it.brandName }.distinct().sorted()
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
                    allItems.find { it.brandName.equals(brand, true) }?.let { onAutoFill(it) }
                })
            }
        }
    )
}
