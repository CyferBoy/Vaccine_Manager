package com.clinic.neochild.ui.vaccine

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clinic.neochild.data.model.Vaccine
import com.clinic.neochild.ui.components.*
import com.clinic.neochild.utils.FirestoreMappers
import com.clinic.neochild.utils.PatientUtils.formatDateForDisplay
import com.clinic.neochild.utils.PatientUtils.parseDate
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

enum class SortOption { BRAND_AZ, QUANTITY, EXPIRY }

/**
 * Screen displaying the list of vaccines in inventory.
 * Supports searching, sorting, and grouping batches by brand name.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VaccineInventoryScreen(
    onBack: () -> Unit = {}, 
    onAddVaccine: () -> Unit = {},
    onEditVaccine: (String) -> Unit = {}
) {
    var allVaccines by remember { mutableStateOf<List<Vaccine>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var currentSort by remember { mutableStateOf(SortOption.BRAND_AZ) }
    var isLoading by remember { mutableStateOf(true) }
    var vaccineToDelete by remember { mutableStateOf<Vaccine?>(null) }
    val db = FirebaseFirestore.getInstance()
    val context = androidx.compose.ui.platform.LocalContext.current

    val filteredVaccines = remember(allVaccines, searchQuery) {
        if (searchQuery.isBlank()) allVaccines
        else allVaccines.filter { 
            it.brandName.contains(searchQuery, ignoreCase = true) || 
            it.type.contains(searchQuery, ignoreCase = true) 
        }
    }

    // Fetch inventory from Firestore with real-time updates
    DisposableEffect(Unit) {
        val listener = db.collection("inventory")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    isLoading = false
                    return@addSnapshotListener
                }
                allVaccines = snapshot?.documents?.mapNotNull { doc ->
                    FirestoreMappers.toVaccine(doc)
                }?.sortedBy { it.brandName.lowercase() } ?: emptyList()
                isLoading = false
            }

        onDispose {
            listener.remove()
        }
    }

    // Standardized Delete Confirmation Dialog
    DeleteConfirmationDialog(
        show = vaccineToDelete != null,
        onDismiss = { vaccineToDelete = null },
        onConfirm = {
            val id = vaccineToDelete?.id ?: ""
            vaccineToDelete = null
            deleteFirestoreDocument(context, "inventory", id)
        },
        title = "Delete Vaccine",
        message = "Are you sure you want to delete ${vaccineToDelete?.brandName} (Batch: ${vaccineToDelete?.batchNumber})? This will remove it from inventory."
    )

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                // Reusable search bar component
                SearchTopAppBar(
                    title = "Vaccine Inventory",
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    isSearchActive = isSearchActive,
                    onSearchActiveChange = { isSearchActive = it },
                    onBack = onBack,
                    placeholder = "Search by brand or type...",
                    actions = {
                        // Standardized sort button
                        SortButton(
                            options = listOf(
                                SortOption.BRAND_AZ to "Brand (A-Z)",
                                SortOption.QUANTITY to "Quantity",
                                SortOption.EXPIRY to "Expiry"
                            ),
                            onSortSelected = { option, _ -> currentSort = option }
                        )
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = onAddVaccine,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Vaccine")
                }
            }
        ) { paddingValues ->
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (filteredVaccines.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Text(if (searchQuery.isEmpty()) "No vaccines in inventory" else "No matching vaccines found")
                }
            } else {
                // Group vaccines by brand name and apply sorting
                val sortedGroups = remember(filteredVaccines, currentSort) {
                    val groups = filteredVaccines.groupBy { 
                        it.brandName.trim().lowercase()
                    }
                    when (currentSort) {
                        SortOption.BRAND_AZ -> groups.toList().sortedBy { it.first }
                        SortOption.QUANTITY -> groups.toList().sortedByDescending { it.second.sumOf { v -> v.stock } }
                        SortOption.EXPIRY -> groups.toList().sortedBy { g -> g.second.minOfOrNull { parseDate(it.expiryDate)?.time ?: Long.MAX_VALUE } }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    sortedGroups.forEach { (_, batchList) ->
                        item {
                            VaccineGroupCard(
                                batches = batchList,
                                onEdit = onEditVaccine,
                                onDelete = { vaccineToDelete = it }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Card representing a group of vaccine batches with the same brand name.
 * Shows total stock and highlights expiry status.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VaccineGroupCard(
    batches: List<Vaccine>,
    onEdit: (String) -> Unit,
    onDelete: (Vaccine) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var menuExpandedId by remember { mutableStateOf<String?>(null) }
    val totalStock = batches.sumOf { it.stock }
    val first = batches.first()
    val brandName = first.brandName.trim()
    
    // Sort batches by expiry date
    val sortedBatches = remember(batches) {
        batches.sortedBy { parseDate(it.expiryDate) }
    }
    
    // Text summary of expiry dates for all batches in the group
    val expirySummary = remember(sortedBatches) {
        if (sortedBatches.size > 1) {
            sortedBatches.joinToString(", ") { "Exp: ${formatDateForDisplay(it.expiryDate)} (${it.stock})" }
        } else {
            "Exp: ${formatDateForDisplay(sortedBatches.firstOrNull()?.expiryDate ?: "")}"
        }
    }

    // Determine card background color based on nearest expiry date
    val cardColor = remember(sortedBatches) {
        val today = Calendar.getInstance()
        val twoMonthsLater = Calendar.getInstance().apply { add(Calendar.MONTH, 2) }
        
        var statusColor: Color? = null
        
        for (batch in sortedBatches) {
            val expDate = parseDate(batch.expiryDate)
            if (expDate != null) {
                val expCal = Calendar.getInstance().apply { time = expDate }
                if (expCal.before(today)) {
                    statusColor = Color(0xFFED9080) // Keep standard Expired Red
                    break // Highest priority
                } else if (expCal.before(twoMonthsLater)) {
                    if (statusColor == null) statusColor = Color(0xFFEBC181) // Keep standard Warning Orange
                }
            }
        }
        statusColor
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .combinedClickable(
                onClick = { expanded = !expanded }, // Single tap to expand/collapse details
                onLongClick = { menuExpandedId = first.id } // Show menu on long press
            ),
        colors = CardDefaults.cardColors(
            containerColor = (cardColor ?: MaterialTheme.colorScheme.surfaceVariant).copy(alpha = 0.7f),
            contentColor = if (cardColor != null) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(16.dp)) {
                    Text(
                        text = brandName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = expirySummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // Detailed batch information (visible when card is expanded)
                    if (expanded) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (first.companyName.isNotEmpty()) {
                            Text(text = "Company: ${first.companyName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        
                        batches.forEach { vaccine ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .combinedClickable(
                                        onClick = { },
                                        onLongClick = { menuExpandedId = vaccine.id } // Long press on specific batch to show menu
                                    ),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = "Batch: ${vaccine.batchNumber}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = "Exp: ${formatDateForDisplay(vaccine.expiryDate)}", style = MaterialTheme.typography.bodySmall)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = "${vaccine.stock}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    }
                                    Row {
                                        Text(text = "Net: ₹${vaccine.netRate}", style = MaterialTheme.typography.bodySmall)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(text = "MRP: ₹${vaccine.mrp}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Total quantity badge on the right
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(80.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "$totalStock",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (totalStock < 5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Show dropdown menu for the long-pressed vaccine item
            val selectedBatch = batches.find { it.id == menuExpandedId }
            if (selectedBatch != null) {
                ActionDropdownMenu(
                    expanded = true,
                    onDismiss = { menuExpandedId = null },
                    onEdit = { onEdit(selectedBatch.id) },
                    onDelete = { onDelete(selectedBatch) }
                )
            }
        }
    }
}
