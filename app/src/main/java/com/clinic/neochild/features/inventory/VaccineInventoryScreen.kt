package com.clinic.neochild.features.inventory

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.clinic.neochild.core.utils.InventoryUtils
import com.clinic.neochild.domain.model.Vaccine
import com.clinic.neochild.core.ui.*
import com.clinic.neochild.core.designsystem.NeoChildTheme
import com.clinic.neochild.core.utils.PatientUtils.formatDateForDisplay
import com.clinic.neochild.core.utils.PatientUtils.parseDate
import java.util.*

enum class SortOption { BRAND_AZ, QUANTITY, EXPIRY }

@Composable
fun VaccineInventoryScreen(
    onBack: () -> Unit = {}, 
    onAddVaccine: () -> Unit = {},
    onEditVaccine: (String) -> Unit = {},
    viewModel: VaccineInventoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var currentSort by rememberSaveable { mutableStateOf(SortOption.BRAND_AZ) }
    var vaccineToDelete by remember { mutableStateOf<Vaccine?>(null) }
    
    val context = androidx.compose.ui.platform.LocalContext.current

    val filteredVaccines = remember(uiState.vaccines, searchQuery) {
        if (searchQuery.isBlank()) uiState.vaccines
        else uiState.vaccines.filter { 
            it.brandName.contains(searchQuery, ignoreCase = true) || 
            it.type.contains(searchQuery, ignoreCase = true) 
        }
    }

    val sortedGroups = remember(filteredVaccines, currentSort) {
        val groups = filteredVaccines.groupBy { it.brandName.trim().lowercase() }
        when (currentSort) {
            SortOption.BRAND_AZ -> groups.toList().sortedBy { it.first }
            SortOption.QUANTITY -> groups.toList().sortedByDescending { it.second.sumOf { v -> v.stock } }
            SortOption.EXPIRY -> groups.toList().sortedBy { g -> g.second.minOfOrNull { parseDate(it.expiryDate)?.time ?: Long.MAX_VALUE } }
        }
    }

    DeleteConfirmationDialog(
        show = vaccineToDelete != null,
        onDismiss = { vaccineToDelete = null },
        onConfirm = {
            vaccineToDelete?.id?.let { deleteFirestoreDocument(context, "inventory", it) }
            vaccineToDelete = null
        },
        title = "Delete Vaccine",
        message = "Are you sure you want to delete ${vaccineToDelete?.brandName} (Batch: ${vaccineToDelete?.batchNumber})?"
    )

    VaccineInventoryContent(
        isLoading = uiState.isLoading,
        lowStockThreshold = uiState.lowStockThreshold,
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        onBack = onBack,
        onAddVaccine = onAddVaccine,
        onEditVaccine = onEditVaccine,
        onDeleteVaccine = { vaccineToDelete = it },
        sortedGroups = sortedGroups,
        onSortSelected = { currentSort = it }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaccineInventoryContent(
    isLoading: Boolean,
    lowStockThreshold: Int,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onAddVaccine: () -> Unit,
    onEditVaccine: (String) -> Unit,
    onDeleteVaccine: (Vaccine) -> Unit,
    sortedGroups: List<Pair<String, List<Vaccine>>>,
    onSortSelected: (SortOption) -> Unit
) {
    var isSearchActive by rememberSaveable { mutableStateOf(false) }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                SearchTopAppBar(
                    title = "Vaccine Inventory",
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    isSearchActive = isSearchActive,
                    onSearchActiveChange = { isSearchActive = it },
                    onBack = onBack,
                    placeholder = "Search by brand or type...",
                    actions = {
                        SortButton(
                            options = listOf(
                                SortOption.BRAND_AZ to "Brand (A-Z)",
                                SortOption.QUANTITY to "Quantity",
                                SortOption.EXPIRY to "Expiry"
                            ),
                            onSortSelected = { option, _ -> onSortSelected(option) }
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
            } else if (sortedGroups.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Text(if (searchQuery.isEmpty()) "No vaccines in inventory" else "No matching vaccines found")
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 80.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items = sortedGroups, key = { it.first }) { group ->
                        VaccineGroupCard(
                            batches = group.second,
                            lowStockThreshold = lowStockThreshold,
                            onEdit = onEditVaccine,
                            onDelete = onDeleteVaccine,
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaccineGroupCard(
    batches: List<Vaccine>,
    lowStockThreshold: Int,
    onEdit: (String) -> Unit,
    onDelete: (Vaccine) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var menuExpandedId by remember { mutableStateOf<String?>(null) }
    
    val totalStock = remember(batches) { batches.sumOf { it.stock } }
    val first = batches.first()
    val brandName = first.brandName.trim()
    
    val sortedBatches = remember(batches) { batches.sortedBy { parseDate(it.expiryDate) } }
    
    val expirySummary = remember(sortedBatches) {
        if (sortedBatches.size > 1) {
            sortedBatches.joinToString(", ") { "${formatDateForDisplay(it.expiryDate)} (${it.stock})" }
        } else {
            formatDateForDisplay(sortedBatches.firstOrNull()?.expiryDate ?: "")
        }
    }

    val cardColor = remember(sortedBatches) {
        var statusColor: Color? = null
        
        for (batch in sortedBatches) {
            if (InventoryUtils.isExpired(batch.expiryDate)) {
                statusColor = Color(0xFFED9080) // Reddish for expired
                break
            } else if (InventoryUtils.isNearExpiry(batch.expiryDate)) {
                if (statusColor == null) statusColor = Color(0xFFEBC181) // Yellowish for near expiry
            }
        }
        statusColor
    }

    val isLowStock = totalStock > 0 && totalStock <= lowStockThreshold

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { expanded = !expanded },
                onLongClick = { menuExpandedId = first.id }
            ),
        colors = CardDefaults.cardColors(
            containerColor = (cardColor ?: if (isLowStock) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant).copy(alpha = 0.7f),
            contentColor = if (cardColor != null) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = brandName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (isLowStock) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.error,
                                shape = androidx.compose.foundation.shape.CircleShape
                            ) {
                                Text(
                                    text = "Low Stock",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onError
                                )
                            }
                        }
                    }
                    
                    val earliestBatch = sortedBatches.firstOrNull()
                    if (earliestBatch != null) {
                        val statusText = when {
                            InventoryUtils.isExpired(earliestBatch.expiryDate) -> "⚠ Expired"
                            InventoryUtils.isNearExpiry(earliestBatch.expiryDate) -> "⚠ Near Expiry (${InventoryUtils.getDaysUntilExpiry(earliestBatch.expiryDate)} days)"
                            else -> "Exp: ${formatDateForDisplay(earliestBatch.expiryDate)}"
                        }
                        Text(text = statusText, style = MaterialTheme.typography.bodyMedium, color = if (InventoryUtils.isExpired(earliestBatch.expiryDate)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    
                    if (expanded) {
                        VaccineGroupDetails(batches = batches, onMenuRequest = { menuExpandedId = it })
                    }
                }
                
                StockBadge(stock = totalStock, lowStockThreshold = lowStockThreshold)
            }

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaccineGroupDetails(batches: List<Vaccine>, onMenuRequest: (String) -> Unit) {
    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
    Spacer(modifier = Modifier.height(8.dp))
    
    val first = batches.first()
    if (first.companyName.isNotEmpty()) {
        Text(text = "Company: ${first.companyName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(12.dp))
    }

    batches.forEach { vaccine ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).combinedClickable(
                onClick = { },
                onLongClick = { onMenuRequest(vaccine.id) }
            ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val isExpired = InventoryUtils.isExpired(vaccine.expiryDate)
                val isNearExpiry = !isExpired && InventoryUtils.isNearExpiry(vaccine.expiryDate)
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Batch: ${vaccine.batchNumber}", 
                        style = MaterialTheme.typography.bodyMedium, 
                        fontWeight = FontWeight.Bold,
                        color = if (isExpired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                    if (isExpired) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("(EXPIRED)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    } else if (isNearExpiry) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("(Near Expiry)", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFA000))
                    }

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

@Composable
private fun StockBadge(stock: Int, lowStockThreshold: Int) {
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
            text = "$stock",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            color = if (stock <= lowStockThreshold) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VaccineInventoryPreview() {
    NeoChildTheme {
        VaccineInventoryContent(
            isLoading = false,
            lowStockThreshold = 5,
            searchQuery = "",
            onSearchQueryChange = {},
            onBack = {},
            onAddVaccine = {},
            onEditVaccine = {},
            onDeleteVaccine = {},
            sortedGroups = listOf(
                "bcg" to listOf(Vaccine("1", "BCG", "SII BCG", "SII", 10, "B123", "2025-01-01", 500.0, 400.0))
            ),
            onSortSelected = {}
        )
    }
}
