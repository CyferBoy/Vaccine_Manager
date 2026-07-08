package com.clinic.neochild.ui.patient

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clinic.neochild.data.model.Patient
import com.clinic.neochild.ui.components.*
import com.clinic.neochild.ui.viewmodel.PatientViewModel
import com.clinic.neochild.utils.FirestoreMappers
import com.clinic.neochild.utils.PatientUtils.calculateAgeLabel
import com.google.firebase.firestore.FirebaseFirestore

enum class PatientSortOption { NAME_AZ, NEWEST }

/**
 * Screen displaying the list of all patients.
 * Allows searching, sorting, and long-pressing to edit or delete.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PatientListScreen(
    onBack: () -> Unit = {},
    onAddPatient: () -> Unit = {},
    onPatientClick: (String) -> Unit = {},
    onEditPatient: (String) -> Unit = {},
    viewModel: PatientViewModel = viewModel(),
) {

    var allPatients by remember { mutableStateOf<List<Patient>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    val patientsWithMissingPrice by viewModel.patientsWithMissingPrice.collectAsState()
    
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(value = false) }
    var patientToDelete by remember { mutableStateOf<Patient?>(null) }
    var currentSort by remember { mutableStateOf(PatientSortOption.NAME_AZ) }
    var menuExpandedPatientId by remember { mutableStateOf<String?>(null) }
    var showMergeConfirmation by remember { mutableStateOf(false) }
    var selectedPatients by remember { mutableStateOf<Set<Patient>>(emptySet()) }
    var showManualMergeDialog by remember { mutableStateOf(false) }
    var mergeMasterPatient by remember { mutableStateOf<Patient?>(null) }
    var isLoadingManualMerge by remember { mutableStateOf(false) }
    var isMergeSelectionMode by remember { mutableStateOf(false) }

    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val listener = db.collection("patients")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    isLoading = false
                    return@addSnapshotListener
                }
                allPatients = snapshot?.documents?.mapNotNull { FirestoreMappers.toPatient(it) } ?: emptyList()
                isLoading = false
            }
        onDispose {
            listener.remove()
        }
    }

    fun mergeDuplicatePatients() {
        db.collection("patients").get().addOnSuccessListener { patientsResult ->
            val allPatientsList = patientsResult.documents.mapNotNull { FirestoreMappers.toPatient(it) }
            
            // Group by name and phone (trimmed and lowercased)
            val groups = allPatientsList.groupBy { 
                it.name.trim().lowercase() + "|" + it.phone.trim()
            }.filter { it.value.size > 1 }

            if (groups.isEmpty()) {
                Toast.makeText(context, "No duplicates found", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }

            var completedGroups = 0
            val totalGroups = groups.size

            groups.forEach { (_, duplicateList) ->
                val master = duplicateList[0]
                val duplicates = duplicateList.drop(1)

                var processedDuplicates = 0
                duplicates.forEach { duplicate ->
                    // Update vaccinations for each duplicate
                    db.collection("vaccinations")
                        .whereEqualTo("patientId", duplicate.id)
                        .get()
                        .addOnSuccessListener { vaccinationsResult ->
                            val batch = db.batch()
                            vaccinationsResult.documents.forEach { vacDoc ->
                                batch.update(vacDoc.reference, "patientId", master.id)
                            }
                            
                            batch.commit().addOnSuccessListener {
                                // Delete the duplicate patient
                                db.collection("patients").document(duplicate.id).delete()
                                    .addOnSuccessListener {
                                        processedDuplicates++
                                        if (processedDuplicates == duplicates.size) {
                                            completedGroups++
                                            if (completedGroups == totalGroups) {
                                                Toast.makeText(context, "Merged $totalGroups groups of duplicates", Toast.LENGTH_LONG).show()
                                                viewModel.refresh()
                                            }
                                        }
                                    }
                            }
                        }
                        .addOnFailureListener {
                            processedDuplicates++
                            if (processedDuplicates == duplicates.size) {
                                completedGroups++
                                if (completedGroups == totalGroups) {
                                    viewModel.refresh()
                                }
                            }
                        }
                }
            }
        }.addOnFailureListener {
            Toast.makeText(context, "Merge failed: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun manualMergePatients(master: Patient, secondary: Patient) {
        isLoadingManualMerge = true
        // 1. Move all vaccinations from secondary to master
        db.collection("vaccinations")
            .whereEqualTo("patientId", secondary.id)
            .get()
            .addOnSuccessListener { vaccinationsResult ->
                val batch = db.batch()
                vaccinationsResult.documents.forEach { vacDoc ->
                    batch.update(vacDoc.reference, "patientId", master.id)
                }
                
                batch.commit().addOnSuccessListener {
                    // 2. Delete the secondary patient
                    db.collection("patients").document(secondary.id).delete()
                        .addOnSuccessListener {
                            isLoadingManualMerge = false
                            showManualMergeDialog = false
                            selectedPatients = emptySet()
                            Toast.makeText(context, "Successfully merged into ${master.name}", Toast.LENGTH_LONG).show()
                            viewModel.refresh()
                        }
                        .addOnFailureListener { e ->
                            isLoadingManualMerge = false
                            Toast.makeText(context, "Failed to delete patient: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }.addOnFailureListener { e ->
                    isLoadingManualMerge = false
                    Toast.makeText(context, "Failed to update vaccinations: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                isLoadingManualMerge = false
                Toast.makeText(context, "Failed to fetch vaccinations: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    val filteredPatients = remember(allPatients, searchQuery, currentSort) {
        val base = if (searchQuery.isBlank()) allPatients
        else allPatients.asSequence().filter { it.name.contains(searchQuery, ignoreCase = true) || it.phone.contains(searchQuery) }.toList()
        
        when (currentSort) {
            PatientSortOption.NAME_AZ -> base.sortedBy { it.name.lowercase() }
            PatientSortOption.NEWEST -> base.asReversed() // Assuming original order from Firestore is somewhat chronological or ID based
        }
    }

    val phoneGroupColors = remember(allPatients) {
        val groupColors = listOf(
            Color(0xFF4CAF50), // Green
            Color(0xFF2196F3), // Blue
            Color(0xFFFF9800), // Orange
            Color(0xFF9C27B0), // Purple
            Color(0xFF00BCD4), // Cyan
            Color(0xFFE91E63), // Pink
            Color(0xFF795548), // Brown
            Color(0xFFCDDC39)  // Lime
        )
        
        val phoneCounts = allPatients.filter { it.phone.isNotBlank() }
            .groupBy { it.phone.trim() }
            .filter { it.value.size > 1 }
        
        phoneCounts.keys.toList().withIndex().associate { (index, phone) ->
            phone to groupColors[index % groupColors.size]
        }
    }

    // Merge Confirmation Dialog
    if (showMergeConfirmation) {
        AlertDialog(
            onDismissRequest = { showMergeConfirmation = false },
            title = { Text("Merge Duplicates") },
            text = { Text("This will merge all patient records with the same name and phone number. This action cannot be undone. Proceed?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showMergeConfirmation = false
                        mergeDuplicatePatients()
                    }
                ) {
                    Text("Merge")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMergeConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Standardized Delete Confirmation Dialog
    DeleteConfirmationDialog(
        show = patientToDelete != null,
        onDismiss = { patientToDelete = null },
        onConfirm = {
            val id = patientToDelete?.id ?: ""
            patientToDelete = null
            viewModel.deletePatient(id)
        },
        title = "Delete Patient",
        message = "Are you sure you want to delete ${patientToDelete?.name}? This will remove all their records."
    )

    if (showManualMergeDialog && (selectedPatients.size == 2)) {
        val patients = selectedPatients.toList()
        AlertDialog(
            onDismissRequest = { if (!isLoadingManualMerge) showManualMergeDialog = false },
            title = { Text("Manual Merge") },
            text = {
                Column {
                    Text("Select the patient profile you want to KEEP. The other will be deleted and its vaccinations moved.")
                    Spacer(modifier = Modifier.height(16.dp))
                    patients.forEach { p ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable { mergeMasterPatient = p }
                                .padding(8.dp)
                        ) {
                            RadioButton(selected = mergeMasterPatient == p, onClick = { mergeMasterPatient = p })
                            Text(p.name + " (${p.id})")
                        }
                    }
                }
            },
            confirmButton = {
                StandardButton(
                    onClick = {
                        val master = mergeMasterPatient
                        val secondary = patients.find { it != master }
                        if (master != null && secondary != null) {
                            manualMergePatients(master, secondary)
                        }
                    },
                    enabled = mergeMasterPatient != null,
                    isLoading = isLoadingManualMerge
                ) {
                    Text("Confirm Merge")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualMergeDialog = false }, enabled = !isLoadingManualMerge) {
                    Text("Cancel")
                }
            }
        )
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                // Reusable search and navigation top bar
                SearchTopAppBar(
                    title = if (isMergeSelectionMode) "Select 2 to Merge" else if (selectedPatients.isNotEmpty()) "${selectedPatients.size} Selected" else "Patients",
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    isSearchActive = isSearchActive,
                    onSearchActiveChange = { isSearchActive = it },
                    onBack = {
                        if (isMergeSelectionMode || selectedPatients.isNotEmpty()) {
                            selectedPatients = emptySet()
                            isMergeSelectionMode = false
                        } else onBack()
                    },
                    actions = {
                        if (selectedPatients.size == 2) {
                            IconButton(onClick = { 
                                mergeMasterPatient = null
                                showManualMergeDialog = true 
                            }) {
                                Icon(Icons.AutoMirrored.Filled.CallMerge, contentDescription = "Merge Selected", tint = Color.Yellow)
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                if (selectedPatients.isEmpty()) {
                    FloatingActionButton(
                        onClick = onAddPatient,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Patient")
                    }
                }
            }
        ) { paddingValues ->
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (filteredPatients.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Text("No patients found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 8.dp),
                    contentPadding = PaddingValues(bottom = 88.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredPatients) { patient ->
                        val isSelected = selectedPatients.contains(patient)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { 
                                        if (isMergeSelectionMode || selectedPatients.isNotEmpty()) {
                                            selectedPatients = if (isSelected) selectedPatients - patient else selectedPatients + patient
                                            // Auto-exit mode if we reach 2? Or just let the user click the top icon.
                                        } else {
                                            onPatientClick(patient.id)
                                        }
                                    },
                                    onLongClick = { 
                                        if (!isMergeSelectionMode && selectedPatients.isEmpty()) {
                                            menuExpandedPatientId = patient.id
                                        }
                                    }
                                ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                             else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                           else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                            border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isMergeSelectionMode || selectedPatients.isNotEmpty()) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { 
                                            selectedPatients = if (isSelected) selectedPatients - patient else selectedPatients + patient
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Surface(
                                    modifier = Modifier.size(48.dp),
                                    shape = CircleShape,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = patient.name.firstOrNull()?.uppercase() ?: "?",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = patient.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val age = if (patient.dob.isNotBlank()) calculateAgeLabel(patient.dob) else null
                                        if (age != null) {
                                            Text(
                                                text = age,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = " • ",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        }
                                        Text(
                                            text = patient.gender.ifEmpty { "Unknown" },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (patientsWithMissingPrice.contains(patient.id) && selectedPatients.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(Color.Red, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                val groupColor = phoneGroupColors[patient.phone.trim()]
                                if (groupColor != null && selectedPatients.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(groupColor, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                
                                if (selectedPatients.isEmpty()) {
                                    Box {
                                        ActionDropdownMenu(
                                            expanded = menuExpandedPatientId == patient.id,
                                            onDismiss = { menuExpandedPatientId = null },
                                            onEdit = { onEditPatient(patient.id) },
                                            onDelete = { patientToDelete = patient },
                                            onMerge = {
                                                selectedPatients = setOf(patient)
                                                isMergeSelectionMode = true
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
