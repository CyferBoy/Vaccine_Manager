package com.clinic.neochild.ui.patient

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clinic.neochild.data.model.Patient
import com.clinic.neochild.ui.components.AppBackground
import com.clinic.neochild.ui.components.DateDropdownPicker
import com.clinic.neochild.ui.components.StandardAutoCompleteField
import com.clinic.neochild.ui.components.StandardButton
import com.clinic.neochild.ui.components.StandardTextField
import com.clinic.neochild.ui.viewmodel.PatientViewModel
import com.clinic.neochild.utils.Constants
import com.clinic.neochild.utils.PatientUtils.calculateDetailedAge
import com.clinic.neochild.utils.PatientUtils.formatDateForDisplay
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPatientScreen(
    patientId: String? = null,
    onBack: () -> Unit = {},
    onNavigateToDetails: (String) -> Unit = {},
    viewModel: PatientViewModel = viewModel(),
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var alternatePhone by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("Male") }
    var address by remember { mutableStateOf("") }
    
    // For age selection
    var ageValue by remember { mutableStateOf("0") }
    var ageUnit by remember { mutableStateOf("Years") }
    
    var isLoading by remember { mutableStateOf(value = false) }
    var isEditMode by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()

    // Load data if in edit mode
    LaunchedEffect(patientId) {
        if (patientId != null) {
            isEditMode = true
            isLoading = true
            db.collection("patients").document(patientId).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        name = doc.getString("name") ?: ""
                        phone = doc.getString("phone") ?: ""
                        alternatePhone = doc.getString("alternatePhone") ?: ""
                        val dobVal = doc["dob"]?.toString() ?: ""
                        dob = formatDateForDisplay(dobVal)
                        gender = doc.getString("gender") ?: "Male"
                        address = doc.getString("address") ?: ""
                        
                        // Calculate age for selectors
                        val detailedAge = calculateDetailedAge(dobVal)
                        ageValue = detailedAge.first.toString()
                        ageUnit = detailedAge.second
                    }
                    isLoading = false
                }
                .addOnFailureListener { isLoading = false }
        }
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(if (isEditMode) "Edit Patient" else "Add Patient") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .imePadding()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                StandardTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Full Name*",
                    placeholder = "Enter patient's name"
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    StandardTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = "Phone Number",
                        placeholder = "e.g., 9876543210",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    StandardTextField(
                        value = alternatePhone,
                        onValueChange = { alternatePhone = it },
                        label = "Alternate Phone",
                        placeholder = "(Optional)",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Date of Birth*",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
                
                DateDropdownPicker(
                    label = "",
                    currentDate = dob,
                    onDateSelected = { 
                        dob = it 
                        // Update age selectors when DOB is picked
                        val detailedAge = calculateDetailedAge(it)
                        ageValue = detailedAge.first.toString()
                        ageUnit = detailedAge.second
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // OR Select Age
                Text(
                    text = "Or Set Age",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    StandardTextField(
                        value = ageValue,
                        onValueChange = { 
                            ageValue = it 
                            // Update DOB based on age selection
                            val cal = Calendar.getInstance()
                            val value = it.toIntOrNull() ?: 0
                            when (ageUnit) {
                                "Years" -> cal.add(Calendar.YEAR, -value)
                                "Months" -> cal.add(Calendar.MONTH, -value)
                                "Weeks" -> cal.add(Calendar.WEEK_OF_YEAR, -value)
                            }
                            dob = SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(cal.time)
                        },
                        label = "Age",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    var expandedUnit by remember { mutableStateOf(false) }
                    StandardAutoCompleteField(
                        value = ageUnit,
                        onValueChange = { },
                        label = "Unit",
                        expanded = expandedUnit,
                        onExpandedChange = { expandedUnit = it },
                        modifier = Modifier.weight(1f),
                        enabled = true,
                        dropdownContent = {
                            listOf("Years", "Months", "Weeks").forEach { unit ->
                                DropdownMenuItem(
                                    text = { Text(unit) },
                                    onClick = {
                                        ageUnit = unit
                                        expandedUnit = false
                                        // Trigger update
                                        val cal = Calendar.getInstance()
                                        val value = ageValue.toIntOrNull() ?: 0
                                        when (unit) {
                                            "Years" -> cal.add(Calendar.YEAR, -value)
                                            "Months" -> cal.add(Calendar.MONTH, -value)
                                            "Weeks" -> cal.add(Calendar.WEEK_OF_YEAR, -value)
                                        }
                                        dob = SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(cal.time)
                                    }
                                )
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Gender*",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Male", "Female", "Other").forEach { g ->
                        FilterChip(
                            selected = gender == g,
                            onClick = { gender = g },
                            label = { Text(g) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                StandardTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = "Address (Optional)",
                    placeholder = "Enter patient's address"
                )

                Spacer(modifier = Modifier.height(32.dp))

                StandardButton(
                    onClick = {
                        if (name.isBlank() || dob.isBlank()) {
                            Toast.makeText(context, "Please fill all required fields", Toast.LENGTH_SHORT).show()
                            return@StandardButton
                        }

                        isLoading = true
                        val patient = Patient(
                            id = patientId ?: UUID.randomUUID().toString(),
                            name = name,
                            phone = phone,
                            alternatePhone = alternatePhone,
                            dob = dob,
                            gender = gender,
                            address = address,
                            registrationDate = SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(Date())
                        )

                        viewModel.savePatient(patient) {
                            isLoading = false
                            Toast.makeText(context, if (isEditMode) "Patient Updated" else "Patient Added", Toast.LENGTH_SHORT).show()
                            if (isEditMode) onBack() else onNavigateToDetails(patient.id)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    isLoading = isLoading
                ) {
                    Text(if (isEditMode) "Update Patient" else "Save Patient", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
