package com.clinic.neochild.feature_sync

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.clinic.neochild.domain.model.SyncItem
import com.clinic.neochild.domain.model.SyncOperation
import com.clinic.neochild.domain.model.SyncStatus
import com.clinic.neochild.core.ui.components.AppBackground
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    onBack: () -> Unit,
    viewModel: SyncViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    AppBackground {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Cloud Sync Manager") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = viewModel::clearHistory) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear History")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            },
            bottomBar = {
                BottomActionRow(
                    onSyncNow = viewModel::syncNow,
                    onRetryFailed = viewModel::retryFailed
                )
            }
        ) { paddingValues ->
            Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                SyncOverviewCard(uiState)
                
                Text(
                    "Sync Queue",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                    fontWeight = FontWeight.Bold
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.queue) { item ->
                        SyncQueueItemRow(item)
                    }
                }
            }
        }
    }
}

@Composable
fun SyncOverviewCard(uiState: SyncUiState) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem("Pending", uiState.queue.count { it.status == SyncStatus.PENDING }.toString())
                StatItem("Failed", uiState.queue.count { it.status == SyncStatus.FAILED }.toString())
                StatItem("Synced", uiState.queue.count { it.status == SyncStatus.SYNCED }.toString())
            }
            Spacer(modifier = Modifier.height(16.dp))
            val dateStr = if (uiState.lastSyncTime > 0) {
                SimpleDateFormat("d MMM, hh:mm a", Locale.ENGLISH).format(Date(uiState.lastSyncTime))
            } else "Never"
            Text("Last successful sync: $dateStr", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun SyncQueueItemRow(item: SyncItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when(item.operation) {
                    SyncOperation.CREATE -> Icons.Default.AddCircle
                    SyncOperation.UPDATE -> Icons.Default.Edit
                    SyncOperation.DELETE -> Icons.Default.Delete
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("${item.entityName}: ${item.entityId.take(8)}...", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                if (item.lastError != null) {
                    Text(item.lastError, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
            StatusBadge(item.status)
        }
    }
}

@Composable
fun StatusBadge(status: SyncStatus) {
    val color = when(status) {
        SyncStatus.SYNCED -> Color(0xFF4CAF50)
        SyncStatus.FAILED -> Color(0xFFF44336)
        SyncStatus.SYNCING -> Color(0xFF2196F3)
        SyncStatus.PENDING -> Color(0xFF9E9E9E)
    }
    Surface(
        color = color.copy(alpha = 0.1f),
        contentColor = color,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = status.name,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun BottomActionRow(onSyncNow: () -> Unit, onRetryFailed: () -> Unit) {
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(onClick = onRetryFailed, modifier = Modifier.weight(1f)) {
                Text("Retry Failed")
            }
            Button(onClick = onSyncNow, modifier = Modifier.weight(1f)) {
                Text("Sync Now")
            }
        }
    }
}
