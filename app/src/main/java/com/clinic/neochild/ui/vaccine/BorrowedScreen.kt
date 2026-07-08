package com.clinic.neochild.ui.vaccine

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.clinic.neochild.data.model.BorrowedVaccine
import com.clinic.neochild.data.model.Vaccine
import com.clinic.neochild.ui.components.StandardAutoCompleteField
import com.clinic.neochild.ui.components.StandardButton
import com.clinic.neochild.ui.components.StandardTextField
import com.clinic.neochild.utils.FirestoreMappers
import com.clinic.neochild.utils.PatientUtils.formatDateForDisplay
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BorrowedScreen(onBack: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("By", "From")
    
    var borrowedList by remember { mutableStateOf<List<BorrowedVaccine>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<BorrowedVaccine?>(null) }

    val db = FirebaseFirestore.getInstance()

    DisposableEffect(Unit) {
        val listener = db.collection("borrowed")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    isLoading = false
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { FirestoreMappers.toBorrowedVaccine(it) } ?: emptyList()
                val fifteenDaysAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -15) }.time
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
                
                borrowedList = list.filter { item ->
                    if (!item.isReturned || item.returnedDate == null) true
                    else {
                        val returnedDate = try { sdf.parse(item.returnedDate) } catch (e: Exception) { null }
                        returnedDate == null || returnedDate.after(fifteenDaysAgo)
                    }
                }.sortedByDescending { it.borrowedDate }
                isLoading = false
            }

        onDispose {
            listener.remove()
        }
    }

    val filteredList = remember(borrowedList, selectedTab) {
        val type = if (selectedTab == 0) "BY" else "FROM"
        borrowedList.filter { it.type == type }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Borrowed Vaccines") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, fontWeight = FontWeight.Bold) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { 
                    editingItem = null
                    showAddDialog = true 
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Borrowed")
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (filteredList.isEmpty()) {
                Text(
                    text = "No records found for \"${tabs[selectedTab]}\".", 
                    modifier = Modifier.align(Alignment.Center), 
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 80.dp, top = 16.dp)
                ) {
                    items(filteredList) { item ->
                        BorrowedItemCard(
                            item = item,
                            onEdit = { 
                                editingItem = item
                                showAddDialog = true
                            },
                            onReturn = {
                                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
                                val updated = item.copy(isReturned = true, returnedDate = sdf.format(Date()))
                                db.collection("borrowed").document(item.id).set(updated)
                            },
                            onDelete = {
                                db.collection("borrowed").document(item.id).delete()
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        BorrowedEditDialog(
            item = editingItem,
            defaultType = if (selectedTab == 0) "BY" else "FROM",
            onDismiss = { showAddDialog = false },
            onSave = { updatedItem ->
                if (updatedItem.id.isEmpty()) {
                    db.collection("borrowed").add(updatedItem)
                } else {
                    db.collection("borrowed").document(updatedItem.id).set(updatedItem)
                }
                showAddDialog = false
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BorrowedItemCard(
    item: BorrowedVaccine,
    onEdit: () -> Unit,
    onReturn: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { },
                onLongClick = { menuExpanded = true }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isReturned) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) 
                             else MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(item.doctorName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (item.isReturned) {
                        Text("RETURNED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
                    }
                }
                Text("Vaccine: ${item.vaccineName}", style = MaterialTheme.typography.bodyMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Date: ${formatDateForDisplay(item.borrowedDate)}", style = MaterialTheme.typography.bodySmall)
                    Text("Batch: ${item.batchNumber}", style = MaterialTheme.typography.bodySmall)
                }
                Text("Expiry: ${formatDateForDisplay(item.expiryDate)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { menuExpanded = false; onEdit() })
                    if (!item.isReturned) {
                        DropdownMenuItem(text = { Text("Mark as Returned") }, onClick = { menuExpanded = false; onReturn() })
                    }
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menuExpanded = false; onDelete() })
                }
            }
        }
    }
}

@Composable
fun BorrowedEditDialog(
    item: BorrowedVaccine?,
    defaultType: String,
    onDismiss: () -> Unit,
    onSave: (BorrowedVaccine) -> Unit
) {
    var doctorName by remember { mutableStateOf(item?.doctorName ?: "") }
    var vaccineName by remember { mutableStateOf(item?.vaccineName ?: "") }
    var borrowedDate by remember { mutableStateOf(item?.borrowedDate ?: SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())) }
    var batchNumber by remember { mutableStateOf(item?.batchNumber ?: "") }
    var expiryDate by remember { mutableStateOf(item?.expiryDate ?: "") }
    var type by remember { mutableStateOf(item?.type ?: defaultType) }
    
    var inventory by remember { mutableStateOf<List<Vaccine>>(emptyList()) }
    val db = FirebaseFirestore.getInstance()
    
    LaunchedEffect(Unit) {
        db.collection("inventory").get().addOnSuccessListener { result ->
            inventory = result.documents.mapNotNull { FirestoreMappers.toVaccine(it) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "Add Borrowed Vaccine" else "Edit Borrowed Vaccine") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Type Selection
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == "BY",
                        onClick = { type = "BY" },
                        label = { Text("Borrowed By") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = type == "FROM",
                        onClick = { type = "FROM" },
                        label = { Text("Borrowed From") },
                        modifier = Modifier.weight(1f)
                    )
                }

                StandardTextField(
                    value = doctorName, 
                    onValueChange = { doctorName = it }, 
                    label = if (type == "BY") "Doctor Name" else "Source/Doctor Name", 
                    modifier = Modifier.fillMaxWidth()
                )
                
                var expanded by remember { mutableStateOf(false) }
                val suggestions = inventory.filter { it.brandName.contains(vaccineName, ignoreCase = true) || it.type.contains(vaccineName, ignoreCase = true) }
                
                StandardAutoCompleteField(
                    value = vaccineName,
                    onValueChange = { newValue -> 
                        vaccineName = newValue
                        expanded = true
                    },
                    label = "Vaccine Name",
                    placeholder = "Search inventory...",
                    expanded = expanded && suggestions.isNotEmpty(),
                    onExpandedChange = { expanded = it },
                    dropdownContent = {
                        suggestions.forEach { v ->
                            DropdownMenuItem(
                                text = { Text("${v.brandName} (${v.type})") },
                                onClick = {
                                    vaccineName = v.brandName
                                    batchNumber = v.batchNumber
                                    expiryDate = v.expiryDate
                                    expanded = false
                                }
                            )
                        }
                    }
                )

                StandardTextField(value = borrowedDate, onValueChange = { borrowedDate = it }, label = "Date (yyyy-MM-dd)", modifier = Modifier.fillMaxWidth())
                StandardTextField(value = batchNumber, onValueChange = { batchNumber = it }, label = "Batch Number", modifier = Modifier.fillMaxWidth())
                StandardTextField(value = expiryDate, onValueChange = { expiryDate = it }, label = "Expiry Date (yyyy-MM-dd)", modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            StandardButton(onClick = {
                onSave(BorrowedVaccine(
                    id = item?.id ?: "",
                    doctorName = doctorName,
                    vaccineName = vaccineName,
                    borrowedDate = borrowedDate,
                    batchNumber = batchNumber,
                    expiryDate = expiryDate,
                    isReturned = item?.isReturned ?: false,
                    returnedDate = item?.returnedDate,
                    type = type
                ))
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
