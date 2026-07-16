package com.clinic.neochild.ui.patient

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.clinic.neochild.domain.model.TimelineEvent
import com.clinic.neochild.domain.model.TimelineEventType
import com.clinic.neochild.ui.components.AppBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    patientId: String,
    onBack: () -> Unit,
    viewModel: TimelineViewModel = hiltViewModel()
) {
    val uiState by viewModel.getTimeline(patientId).collectAsState()

    AppBackground {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Patient Timeline", style = MaterialTheme.typography.titleMedium)
                            Text(uiState.patient?.name ?: "", style = MaterialTheme.typography.bodySmall)
                        }
                    },
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
            }
        ) { paddingValues ->
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(uiState.events) { event ->
                        TimelineItem(event)
                    }
                }
            }
        }
    }
}

@Composable
fun TimelineItem(event: TimelineEvent) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = getEventColor(event.type),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getEventIcon(event.type),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(event.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    Text(event.dateDisplay, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (event.subtitle != null) {
                    Text(event.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                if (event.extraInfo != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(event.extraInfo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (event.performedBy != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("By: ${event.performedBy}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Light)
                }
            }
        }
    }
}

fun getEventColor(type: TimelineEventType): Color = when (type) {
    TimelineEventType.REGISTRATION -> Color(0xFF2196F3)
    TimelineEventType.VACCINATION -> Color(0xFF4CAF50)
    TimelineEventType.FOLLOW_UP_SCHEDULED -> Color(0xFFFF9800)
    TimelineEventType.FOLLOW_UP_COMPLETED -> Color(0xFF8BC34A)
    TimelineEventType.FOLLOW_UP_RESCHEDULED -> Color(0xFF9C27B0)
    TimelineEventType.FOLLOW_UP_DISMISSED -> Color(0xFF757575)
    TimelineEventType.EXTERNAL_VACCINATION -> Color(0xFF009688)
    TimelineEventType.AUDIT_LOG -> Color(0xFF607D8B)
}

fun getEventIcon(type: TimelineEventType) = when (type) {
    TimelineEventType.REGISTRATION -> Icons.Default.PersonAdd
    TimelineEventType.VACCINATION -> Icons.Default.Vaccines
    TimelineEventType.FOLLOW_UP_SCHEDULED -> Icons.Default.Event
    TimelineEventType.FOLLOW_UP_COMPLETED -> Icons.Default.CheckCircle
    TimelineEventType.FOLLOW_UP_RESCHEDULED -> Icons.Default.EventRepeat
    TimelineEventType.FOLLOW_UP_DISMISSED -> Icons.Default.NotificationsOff
    TimelineEventType.EXTERNAL_VACCINATION -> Icons.Default.Public
    TimelineEventType.AUDIT_LOG -> Icons.Default.History
}
