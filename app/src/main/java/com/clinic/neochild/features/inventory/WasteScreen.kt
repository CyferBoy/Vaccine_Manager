package com.clinic.neochild.features.inventory

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.clinic.neochild.domain.model.Vaccine
import com.clinic.neochild.domain.model.WasteRecord
import com.clinic.neochild.core.common.*
import com.clinic.neochild.core.designsystem.NeoChildTheme
import com.clinic.neochild.core.constants.Constants
import com.clinic.neochild.core.utils.PatientUtils.formatDateForDisplay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun WasteScreen(
    onBack: () -> Unit,
    viewModel: WasteViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    WasteContent(
        uiState = uiState,
        onBack = onBack,
        onAddClick = { showAddDialog = true },
        onDeleteRequest = { viewModel.deleteWaste(it.id) }
    )

    if (showAddDialog) {
        AddWasteDialog(
            inventory = uiState.inventory,
            isSaving = uiState.isSaving,
            onDismiss = { showAddDialog = false },
            onSave = { vaccineId, brand, batch, exp, date, reason ->
                viewModel.recordWaste(vaccineId, brand, batch, exp, date, reason) {
                    Toast.makeText(context, "Waste recorded", Toast.LENGTH_SHORT).show()
                    showAddDialog = false
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WasteContent(
    uiState: WasteUiState,
    onBack: () -> Unit,
    onAddClick: () -> Unit,
    onDeleteRequest: (WasteRecord) -> Unit
) {
    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
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
                    onClick = onAddClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Waste")
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                if (uiState.isLoading && uiState.wasteRecords.isEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (uiState.wasteRecords.isEmpty()) {
                    Text("No waste records found", modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 88.dp, top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.wasteRecords, key = { it.id }) { record ->
                            WasteItemCard(record, onDelete = { onDeleteRequest(record) }, modifier = Modifier.animateItem())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WasteItemCard(record: WasteRecord, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
                val dateDisplay = remember(record.dateWasted) { formatDateForDisplay(record.dateWasted) }
                Text("Date: $dateDisplay", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun AddWasteDialog(
    inventory: List<Vaccine>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String) -> Unit
) {
    var selectedBrand by rememberSaveable { mutableStateOf("") }
    var selectedVaccineId by rememberSaveable { mutableStateOf("") }
    var batchNumber by rememberSaveable { mutableStateOf("") }
    var expiryDate by rememberSaveable { mutableStateOf("") }
    
    val today = remember { SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(Date()) }
    var dateWasted by rememberSaveable { mutableStateOf(today) }
    var reason by rememberSaveable { mutableStateOf("Administration Waste") }
    
    var expandedBrand by rememberSaveable { mutableStateOf(false) }

    val availableBrands = remember(selectedBrand, inventory) {
        inventory.filter { 
            it.brandName.contains(selectedBrand, ignoreCase = true) ||
            it.type.contains(selectedBrand, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
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

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StandardTextField(value = batchNumber, onValueChange = {}, label = "Batch", readOnly = true, modifier = Modifier.weight(1f))
                    StandardTextField(value = expiryDate, onValueChange = {}, label = "Exp", readOnly = true, modifier = Modifier.weight(1f))
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
                onClick = { onSave(selectedVaccineId, selectedBrand, batchNumber, expiryDate, dateWasted, reason) },
                enabled = selectedVaccineId.isNotEmpty() && !isSaving,
                isLoading = isSaving
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun WastePreview() {
    NeoChildTheme {
        WasteContent(
            uiState = WasteUiState(
                isLoading = false,
                wasteRecords = listOf(WasteRecord("1", "v1", "BCG", "B123", "2025-01-01", "2024-01-01", "Expired", 1))
            ),
            onBack = {},
            onAddClick = {},
            onDeleteRequest = {}
        )
    }
}
