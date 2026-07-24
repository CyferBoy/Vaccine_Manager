package com.clinic.neochild.features.inventory

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.clinic.neochild.core.ui.AppBackground
import com.clinic.neochild.core.ui.StandardButton
import com.clinic.neochild.core.ui.StandardTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVaccineScreen(
    vaccineId: String? = null,
    onBack: () -> Unit = {},
    viewModel: AddVaccineViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var brandName by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("") }
    var companyName by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(vaccineId) {
        if (vaccineId != null) {
            viewModel.loadVaccine(vaccineId)
        }
    }

    LaunchedEffect(uiState.vaccine) {
        uiState.vaccine?.let {
            brandName = it.brandName
            type = it.type
            companyName = it.companyName
        }
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            Toast.makeText(context, "Vaccine definition saved", Toast.LENGTH_SHORT).show()
            viewModel.resetState()
            onBack()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.resetState()
        }
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(if (vaccineId != null) "Edit Vaccine" else "Add Vaccine") },
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
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StandardTextField(
                    value = brandName,
                    onValueChange = { brandName = it },
                    label = "Brand Name*",
                    placeholder = "e.g. Pentaxim"
                )

                StandardTextField(
                    value = type,
                    onValueChange = { type = it },
                    label = "Vaccine Type*",
                    placeholder = "e.g. DTaP-IPV-Hib"
                )

                StandardTextField(
                    value = companyName,
                    onValueChange = { companyName = it },
                    label = "Manufacturer*",
                    placeholder = "e.g. Sanofi"
                )

                Spacer(modifier = Modifier.height(24.dp))

                StandardButton(
                    onClick = {
                        if (brandName.isBlank() || type.isBlank() || companyName.isBlank()) {
                            Toast.makeText(context, "Please fill all required fields", Toast.LENGTH_SHORT).show()
                            return@StandardButton
                        }
                        viewModel.saveVaccine(vaccineId, brandName, type, companyName)
                    },
                    isLoading = uiState.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (vaccineId != null) "Update Vaccine" else "Create Vaccine")
                }
            }
        }
    }
}
