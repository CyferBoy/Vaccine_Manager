package com.clinic.neochild.ui.auth

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clinic.neochild.data.model.Staff
import com.clinic.neochild.ui.components.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ManageStaffScreen(
    onBack: () -> Unit,
    viewModel: AdminViewModel = viewModel()
) {
    val staffList by viewModel.staffList.collectAsState()
    val isLoading by viewModel.isLoading
    val error by viewModel.error
    val success by viewModel.success

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var staffToDelete by remember { mutableStateOf<Staff?>(null) }
    var menuExpandedStaffId by remember { mutableStateOf<String?>(null) }

    val filteredStaff = remember(staffList, searchQuery) {
        if (searchQuery.isBlank()) staffList
        else staffList.filter { it.name.contains(searchQuery, ignoreCase = true) || it.email.contains(searchQuery, ignoreCase = true) }
    }

    // Add Staff Dialog
    if (showAddDialog) {
        var newStaffName by remember { mutableStateOf("") }
        var newStaffEmail by remember { mutableStateOf("") }
        var newStaffPassword by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false; viewModel.clearMessages() },
            title = { Text("Add New Staff") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StandardTextField(
                        value = newStaffName,
                        onValueChange = { newStaffName = it },
                        label = "Full Name",
                        placeholder = "Enter staff name"
                    )
                    StandardTextField(
                        value = newStaffEmail,
                        onValueChange = { newStaffEmail = it },
                        label = "Email",
                        placeholder = "Enter staff email"
                    )
                    StandardTextField(
                        value = newStaffPassword,
                        onValueChange = { newStaffPassword = it },
                        label = "Temporary Password",
                        placeholder = "Enter password",
                        visualTransformation = PasswordVisualTransformation()
                    )
                    
                    if (error != null) {
                        Text(text = error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                    if (success != null) {
                        Text(text = success!!, color = Color(0xFF4CAF50), fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createStaffAccount(newStaffName, newStaffEmail, newStaffPassword)
                    },
                    enabled = !isLoading
                ) {
                    if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    else Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false; viewModel.clearMessages() }) {
                    Text("Cancel")
                }
            }
        )
        
        // Close dialog on success
        LaunchedEffect(success) {
            if (success != null) {
                kotlinx.coroutines.delay(1500)
                showAddDialog = false
                viewModel.clearMessages()
            }
        }
    }

    // Delete Confirmation
    DeleteConfirmationDialog(
        show = staffToDelete != null,
        onDismiss = { staffToDelete = null },
        onConfirm = {
            staffToDelete?.let { viewModel.deleteStaff(it.id) }
            staffToDelete = null
        },
        title = "Delete Staff",
        message = "Are you sure you want to delete ${staffToDelete?.name}? This will remove their record from Firestore."
    )

    AppBackground {
        Scaffold(
            topBar = {
                SearchTopAppBar(
                    title = "Manage Staff",
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    isSearchActive = isSearchActive,
                    onSearchActiveChange = { isSearchActive = it },
                    onBack = onBack
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Staff")
                }
            },
            containerColor = Color.Transparent
        ) { padding ->
            if (isLoading && staffList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (filteredStaff.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("No staff members found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 8.dp),
                    contentPadding = PaddingValues(bottom = 88.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredStaff) { staff ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { /* Could show details */ },
                                    onLongClick = { menuExpandedStaffId = staff.id }
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(48.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = staff.name.firstOrNull()?.uppercase() ?: "?",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = staff.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = staff.email,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Box {
                                    IconButton(onClick = { menuExpandedStaffId = staff.id }) {
                                        Icon(Icons.Default.Person, contentDescription = "Actions")
                                    }
                                    
                                    ActionDropdownMenu(
                                        expanded = menuExpandedStaffId == staff.id,
                                        onDismiss = { menuExpandedStaffId = null },
                                        onEdit = { /* Not implemented yet */ },
                                        onDelete = { staffToDelete = staff }
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
