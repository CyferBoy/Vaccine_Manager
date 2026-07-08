package com.clinic.neochild.ui.statistics

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.clinic.neochild.data.model.Patient
import com.clinic.neochild.data.model.Vaccination
import com.clinic.neochild.utils.FirestoreMappers
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DueScreen(onBack: () -> Unit) {
    var patients by remember { mutableStateOf<List<Patient>>(emptyList()) }
    var vaccinations by remember { mutableStateOf<List<Vaccination>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedFilter by remember { mutableStateOf("Today") }

    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val patientsListener = db.collection("patients")
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                patients = snapshot?.documents?.mapNotNull { FirestoreMappers.toPatient(it) } ?: emptyList()
            }

        val vaccinationsListener = db.collection("vaccinations")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    isLoading = false
                    return@addSnapshotListener
                }
                vaccinations = snapshot?.documents?.mapNotNull { FirestoreMappers.toVaccination(it) } ?: emptyList()
                isLoading = false
            }

        onDispose {
            patientsListener.remove()
            vaccinationsListener.remove()
        }
    }

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
            if (isLoading && vaccinations.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                DueTab(
                    patients = patients, 
                    vaccinations = vaccinations,
                    initialFilter = selectedFilter,
                    onFilterChanged = { selectedFilter = it }
                ) { updatedVax ->
                    db.collection("vaccinations").document(updatedVax.id)
                        .update("isDone", updatedVax.isDone)
                        .addOnSuccessListener { 
                            Toast.makeText(context, "Marked as Done", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(context, "Update failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            }
        }
    }
}
