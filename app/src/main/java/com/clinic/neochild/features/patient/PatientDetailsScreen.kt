package com.clinic.neochild.features.patient

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.clinic.neochild.domain.model.Vaccination
import com.clinic.neochild.core.ui.*
import com.clinic.neochild.core.utils.PatientUtils.parseDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientDetailsScreen(
    patientId: String, 
    onBack: () -> Unit = {}, 
    onAddVaccine: (String) -> Unit = {},
    onEditVaccination: (String) -> Unit = {},
    onNavigateToTimeline: (String) -> Unit = {},
    viewModel: PatientViewModel = hiltViewModel()
) {
    val allPatients by viewModel.allPatients.collectAsState()
    val patient = remember(patientId, allPatients) { allPatients.find { it.id == patientId } }
    
    val allVaccinations by viewModel.allVaccinations.collectAsState()
    
    // Derived state for patient vaccinations
    val patientVaccinations = remember(patientId, allVaccinations) {
        allVaccinations.filter { it.patientId == patientId }.sortedByDescending { parseDate(it.dateGiven) }
    }

    var vaccinationToDelete by remember { mutableStateOf<Vaccination?>(null) }

    DeleteConfirmationDialog(
        show = vaccinationToDelete != null,
        onDismiss = { vaccinationToDelete = null },
        onConfirm = {
            vaccinationToDelete?.id?.let { viewModel.deleteVaccination(it) }
            vaccinationToDelete = null
        },
        title = "Delete Vaccination",
        message = "Are you sure you want to delete this vaccination record?"
    )

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Patient Details") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { onNavigateToTimeline(patientId) }) {
                            Icon(Icons.Default.History, contentDescription = "Timeline", tint = MaterialTheme.colorScheme.onPrimary)
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
                    onClick = { onAddVaccine(patientId) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Vaccination")
                }
            }
        ) { paddingValues ->
            if (patient == null) {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Text("Patient not found", style = MaterialTheme.typography.titleLarge)
                }
            } else {
                PatientDetailsContent(
                    paddingValues = paddingValues,
                    patient = patient,
                    vaccinations = patientVaccinations,
                    onEditVaccination = onEditVaccination,
                    onDeleteVaccination = { vaccinationToDelete = it },
                    onMarkAsDone = { viewModel.markAsDone(it) }
                )
            }
        }
    }
}
