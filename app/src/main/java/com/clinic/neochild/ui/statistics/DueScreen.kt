package com.clinic.neochild.ui.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.clinic.neochild.ui.components.AppBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DueScreen(
    onBack: () -> Unit,
    viewModel: DueViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    AppBackground {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Due Vaccinations") },
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
            Box(modifier = Modifier.padding(paddingValues).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    DueTab(
                        patients = uiState.patients, 
                        filteredVaccinations = uiState.filteredVaccinations,
                        overdueCount = uiState.overdueCount,
                        initialFilter = uiState.selectedFilter,
                        onFilterChanged = viewModel::updateFilter,
                        onSearchQueryChanged = viewModel::updateSearchQuery,
                        onMarkAsDone = viewModel::markAsDone,
                        onDismissReminder = viewModel::dismissReminder,
                        onReschedule = viewModel::rescheduleVaccination,
                        onVaccinatedElsewhere = viewModel::markVaccinatedElsewhere
                    )
                }
            }
        }
    }
}
