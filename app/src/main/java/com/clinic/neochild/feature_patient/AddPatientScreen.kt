package com.clinic.neochild.feature_patient

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.clinic.neochild.domain.model.Patient
import com.clinic.neochild.core.ui.components.*
import com.clinic.neochild.core.ui.theme.NeoChildTheme
import com.clinic.neochild.ui.viewmodel.PatientViewModel
import com.clinic.neochild.core.common.Constants
import com.clinic.neochild.core.utils.PatientUtils.calculateDetailedAge
import com.clinic.neochild.core.utils.PatientUtils.formatDateForDisplay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AddPatientScreen(
    patientId: String? = null,
    onBack: () -> Unit = {},
    onNavigateToDetails: (String) -> Unit = {},
    viewModel: PatientViewModel = hiltViewModel(),
) {
    // Form State
    var clinicId by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var parentName by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var alternatePhone by rememberSaveable { mutableStateOf("") }
    var dob by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf("Male") }
    var village by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    
    // For age selection
    var ageValue by rememberSaveable { mutableStateOf("0") }
    var ageUnit by rememberSaveable { mutableStateOf("Years") }
    
    var isLoading by rememberSaveable { mutableStateOf(false) }
    var isEditMode by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current

    // Load data if in edit mode
    LaunchedEffect(patientId) {
        if (patientId != null) {
            isEditMode = true
            isLoading = true
            val patient = viewModel.getPatientById(patientId)
            if (patient != null) {
                clinicId = patient.patientClinicId
                name = patient.name
                parentName = patient.parentName
                phone = patient.phone
                alternatePhone = patient.alternatePhone
                dob = formatDateForDisplay(patient.dob)
                gender = patient.gender
                village = patient.village
                address = patient.address
                
                val detailedAge = calculateDetailedAge(patient.dob)
                ageValue = detailedAge.first.toString()
                ageUnit = detailedAge.second
            }
            isLoading = false
        }
    }

    AddPatientContent(
        isEditMode = isEditMode,
        onBack = onBack,
        clinicId = clinicId,
        onClinicIdChange = { clinicId = it },
        name = name,
        onNameChange = { name = it },
        parentName = parentName,
        onParentNameChange = { parentName = it },
        phone = phone,
        onPhoneChange = { phone = it },
        alternatePhone = alternatePhone,
        onAlternatePhoneChange = { alternatePhone = it },
        dob = dob,
        onDobChange = { selectedDob ->
            dob = selectedDob
            val detailedAge = calculateDetailedAge(selectedDob)
            ageValue = detailedAge.first.toString()
            ageUnit = detailedAge.second
        },
        ageValue = ageValue,
        onAgeValueChange = { newVal ->
            ageValue = newVal
            val cal = Calendar.getInstance()
            val value = newVal.toIntOrNull() ?: 0
            when (ageUnit) {
                "Years" -> cal.add(Calendar.YEAR, -value)
                "Months" -> cal.add(Calendar.MONTH, -value)
                "Weeks" -> cal.add(Calendar.WEEK_OF_YEAR, -value)
            }
            dob = SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(cal.time)
        },
        ageUnit = ageUnit,
        onAgeUnitChange = { newUnit ->
            ageUnit = newUnit
            val cal = Calendar.getInstance()
            val value = ageValue.toIntOrNull() ?: 0
            when (newUnit) {
                "Years" -> cal.add(Calendar.YEAR, -value)
                "Months" -> cal.add(Calendar.MONTH, -value)
                "Weeks" -> cal.add(Calendar.WEEK_OF_YEAR, -value)
            }
            dob = SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(cal.time)
        },
        gender = gender,
        onGenderChange = { gender = it },
        village = village,
        onVillageChange = { village = it },
        address = address,
        onAddressChange = { address = it },
        isLoading = isLoading,
        onSave = {
            if (name.isBlank() || dob.isBlank()) {
                Toast.makeText(context, "Please fill all required fields", Toast.LENGTH_SHORT).show()
            } else {
                isLoading = true
                val patient = Patient(
                    id = patientId ?: UUID.randomUUID().toString(),
                    patientClinicId = clinicId,
                    name = name,
                    parentName = parentName,
                    phone = phone,
                    alternatePhone = alternatePhone,
                    dob = dob,
                    gender = gender,
                    village = village,
                    address = address,
                    registrationDate = SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(Date())
                )

                viewModel.savePatient(patient) {
                    isLoading = false
                    Toast.makeText(context, if (isEditMode) "Patient Updated" else "Patient Added", Toast.LENGTH_SHORT).show()
                    if (isEditMode) onBack() else onNavigateToDetails(patient.id)
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPatientContent(
    isEditMode: Boolean,
    onBack: () -> Unit,
    clinicId: String,
    onClinicIdChange: (String) -> Unit,
    name: String,
    onNameChange: (String) -> Unit,
    parentName: String,
    onParentNameChange: (String) -> Unit,
    phone: String,
    onPhoneChange: (String) -> Unit,
    alternatePhone: String,
    onAlternatePhoneChange: (String) -> Unit,
    dob: String,
    onDobChange: (String) -> Unit,
    ageValue: String,
    onAgeValueChange: (String) -> Unit,
    ageUnit: String,
    onAgeUnitChange: (String) -> Unit,
    gender: String,
    onGenderChange: (String) -> Unit,
    village: String,
    onVillageChange: (String) -> Unit,
    address: String,
    onAddressChange: (String) -> Unit,
    isLoading: Boolean,
    onSave: () -> Unit
) {
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
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StandardTextField(
                        value = clinicId,
                        onValueChange = onClinicIdChange,
                        label = "Patient ID (Optional)",
                        placeholder = "e.g. NEO-001",
                        modifier = Modifier.weight(1f)
                    )
                    StandardTextField(
                        value = name,
                        onValueChange = onNameChange,
                        label = "Full Name*",
                        placeholder = "Enter patient's name",
                        modifier = Modifier.weight(2f)
                    )
                }

                StandardTextField(
                    value = parentName,
                    onValueChange = onParentNameChange,
                    label = "Parent/Guardian Name",
                    placeholder = "Enter parent's name"
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StandardTextField(
                        value = phone,
                        onValueChange = onPhoneChange,
                        label = "Phone Number",
                        placeholder = "e.g., 9876543210",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1f)
                    )
                    StandardTextField(
                        value = alternatePhone,
                        onValueChange = onAlternatePhoneChange,
                        label = "Alternate Phone",
                        placeholder = "(Optional)",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1f)
                    )
                }

                DateSelectionSection(
                    dob = dob,
                    onDobChange = onDobChange,
                    ageValue = ageValue,
                    onAgeValueChange = onAgeValueChange,
                    ageUnit = ageUnit,
                    onAgeUnitChange = onAgeUnitChange
                )

                GenderSelectionSection(
                    selectedGender = gender,
                    onGenderChange = onGenderChange
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StandardTextField(
                        value = village,
                        onValueChange = onVillageChange,
                        label = "Village/City",
                        placeholder = "e.g. Sahibganj",
                        modifier = Modifier.weight(1f)
                    )
                    StandardTextField(
                        value = address,
                        onValueChange = onAddressChange,
                        label = "Address (Optional)",
                        placeholder = "Full address",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                StandardButton(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth(),
                    isLoading = isLoading
                ) {
                    Text(if (isEditMode) "Update Patient" else "Save Patient", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun DateSelectionSection(
    dob: String,
    onDobChange: (String) -> Unit,
    ageValue: String,
    onAgeValueChange: (String) -> Unit,
    ageUnit: String,
    onAgeUnitChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Date of Birth*",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp)
        )
        
        DateDropdownPicker(
            label = "",
            currentDate = dob,
            onDateSelected = onDobChange,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "Or Set Age",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StandardTextField(
                value = ageValue,
                onValueChange = onAgeValueChange,
                label = "Age",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            
            var expandedUnit by rememberSaveable { mutableStateOf(false) }
            StandardAutoCompleteField(
                value = ageUnit,
                onValueChange = { },
                label = "Unit",
                expanded = expandedUnit,
                onExpandedChange = { expandedUnit = it },
                modifier = Modifier.weight(1f),
                dropdownContent = {
                    listOf("Years", "Months", "Weeks").forEach { unit ->
                        DropdownMenuItem(
                            text = { Text(unit) },
                            onClick = {
                                onAgeUnitChange(unit)
                                expandedUnit = false
                            }
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun GenderSelectionSection(
    selectedGender: String,
    onGenderChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Gender*",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp)
        )
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Male", "Female", "Other").forEach { g ->
                FilterChip(
                    selected = selectedGender == g,
                    onClick = { onGenderChange(g) },
                    label = { Text(g) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AddPatientPreview() {
    NeoChildTheme {
        AddPatientContent(
            isEditMode = false,
            onBack = {},
            clinicId = "NEO-001",
            onClinicIdChange = {},
            name = "John Doe",
            onNameChange = {},
            parentName = "Jane Doe",
            onParentNameChange = {},
            phone = "1234567890",
            onPhoneChange = {},
            alternatePhone = "",
            onAlternatePhoneChange = {},
            dob = "1 Jan 2020",
            onDobChange = {},
            ageValue = "4",
            onAgeValueChange = {},
            ageUnit = "Years",
            onAgeUnitChange = {},
            gender = "Male",
            onGenderChange = {},
            village = "Sahibganj",
            onVillageChange = {},
            address = "Sahibganj",
            onAddressChange = {},
            isLoading = false,
            onSave = {}
        )
    }
}
