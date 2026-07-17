package com.clinic.neochild.features.sync

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.clinic.neochild.core.ui.AppBackground
import com.clinic.neochild.core.model.SyncStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    onBack: () -> Unit,
    viewModel: SyncViewModel = hiltViewModel()
) {
    val syncQueue by viewModel.syncQueue.collectAsState()

    AppBackground {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Cloud Synchronization") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.clearSynced() }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Synced")
                        }
                        IconButton(onClick = { viewModel.processSync() }) {
                            Icon(Icons.Default.CloudSync, contentDescription = "Sync Now")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        ) { paddingValues ->
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                SyncSummary(syncQueue.size, syncQueue.count { it.status == SyncStatus.FAILED })
                
                if (syncQueue.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Sync queue is empty", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(syncQueue, key = { it.id }) { item ->
                            SyncItemCard(item)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncSummary(total: Int, failed: Int) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Pending Items", style = MaterialTheme.typography.labelMedium)
                Text("$total", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            if (failed > 0) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("Failed", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    Text("$failed", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun SyncItemCard(item: com.clinic.neochild.core.model.SyncItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (item.status) {
                SyncStatus.FAILED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                SyncStatus.SYNCING -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.entityName, fontWeight = FontWeight.Bold)
                Text(item.status.name, style = MaterialTheme.typography.labelSmall, color = getStatusColor(item.status))
            }
            Text("Action: ${item.operation.name}", style = MaterialTheme.typography.bodySmall)
            Text("ID: ${item.entityId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (item.lastError != null) {
                Text("Error: ${item.lastError}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun getStatusColor(status: SyncStatus): Color = when (status) {
    SyncStatus.PENDING -> Color.Gray
    SyncStatus.SYNCING -> MaterialTheme.colorScheme.primary
    SyncStatus.SYNCED -> Color(0xFF4CAF50)
    SyncStatus.FAILED -> MaterialTheme.colorScheme.error
}
