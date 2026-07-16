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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.clinic.neochild.domain.model.Staff
import com.clinic.neochild.core.ui.components.*
import com.clinic.neochild.core.ui.theme.NeoChildTheme

@Composable
fun ManageStaffScreen(
    onBack: () -> Unit,
    viewModel: AdminViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var staffToDelete by remember { mutableStateOf<Staff?>(null) }

    val filteredStaff = remember(uiState.staffList, searchQuery) {
        if (searchQuery.isBlank()) uiState.staffList
        else uiState.staffList.filter { 
            it.name.contains(searchQuery, ignoreCase = true) || 
            it.email.contains(searchQuery, ignoreCase = true) 
        }
    }

    ManageStaffContent(
        uiState = uiState,
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        isSearchActive = isSearchActive,
        onSearchActiveChange = { isSearchActive = it },
        onBack = onBack,
        onAddClick = { showAddDialog = true },
        staffToDelete = staffToDelete,
        onStaffDeleteRequest = { staffToDelete = it },
        onStaffDeleteConfirm = { 
            staffToDelete?.let { viewModel.deleteStaff(it.id) }
            staffToDelete = null
        },
        onStaffDeleteCancel = { staffToDelete = null },
        filteredStaff = filteredStaff,
        showAddDialog = showAddDialog,
        onAddDialogDismiss = { 
            showAddDialog = false
            viewModel.clearMessages()
        },
        onAddStaff = { name, email, pass ->
            viewModel.createStaffAccount(name, email, pass)
        },
        onClearMessages = { viewModel.clearMessages() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageStaffContent(
    uiState: AdminUiState,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isSearchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onAddClick: () -> Unit,
    staffToDelete: Staff?,
    onStaffDeleteRequest: (Staff) -> Unit,
    onStaffDeleteConfirm: () -> Unit,
    onStaffDeleteCancel: () -> Unit,
    filteredStaff: List<Staff>,
    showAddDialog: Boolean,
    onAddDialogDismiss: () -> Unit,
    onAddStaff: (String, String, String) -> Unit,
    onClearMessages: () -> Unit
) {
    DeleteConfirmationDialog(
        show = staffToDelete != null,
        onDismiss = onStaffDeleteCancel,
        onConfirm = onStaffDeleteConfirm,
        title = "Delete Staff",
        message = "Are you sure you want to delete ${staffToDelete?.name}? This will remove their record from Firestore."
    )

    if (showAddDialog) {
        AddStaffDialog(
            isLoading = uiState.isLoading,
            error = uiState.error,
            success = uiState.success,
            onDismiss = onAddDialogDismiss,
            onAdd = onAddStaff
        )
    }

    AppBackground {
        Scaffold(
            topBar = {
                SearchTopAppBar(
                    title = "Manage Staff",
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    isSearchActive = isSearchActive,
                    onSearchActiveChange = onSearchActiveChange,
                    onBack = onBack
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = onAddClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Staff")
                }
            },
            containerColor = Color.Transparent
        ) { padding ->
            StaffList(
                padding = padding,
                isLoading = uiState.isLoading && uiState.staffList.isEmpty(),
                staffMembers = filteredStaff,
                onDeleteRequest = onStaffDeleteRequest
            )
        }
    }
}

@Composable
private fun StaffList(
    padding: PaddingValues,
    isLoading: Boolean,
    staffMembers: List<Staff>,
    onDeleteRequest: (Staff) -> Unit
) {
    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (staffMembers.isEmpty()) {
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
            items(staffMembers, key = { it.id }) { staff ->
                StaffCard(staff = staff, onDeleteRequest = { onDeleteRequest(staff) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StaffCard(
    staff: Staff,
    onDeleteRequest: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { /* Could show details */ },
                onLongClick = { menuExpanded = true }
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
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.Person, contentDescription = "Actions")
                }
                
                ActionDropdownMenu(
                    expanded = menuExpanded,
                    onDismiss = { menuExpanded = false },
                    onEdit = { /* Not implemented yet */ },
                    onDelete = onDeleteRequest
                )
            }
        }
    }
}

@Composable
private fun AddStaffDialog(
    isLoading: Boolean,
    error: String?,
    success: String?,
    onDismiss: () -> Unit,
    onAdd: (String, String, String) -> Unit
) {
    var newStaffName by rememberSaveable { mutableStateOf("") }
    var newStaffEmail by rememberSaveable { mutableStateOf("") }
    var newStaffPassword by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
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
                    Text(text = error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
                if (success != null) {
                    Text(text = success, color = Color(0xFF4CAF50), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(newStaffName, newStaffEmail, newStaffPassword) },
                enabled = !isLoading
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                else Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun ManageStaffPreview() {
    NeoChildTheme {
        ManageStaffContent(
            uiState = AdminUiState(
                staffList = listOf(Staff("1", "admin@clinic.com", "Admin User", "Admin", 0L))
            ),
            searchQuery = "",
            onSearchQueryChange = {},
            isSearchActive = false,
            onSearchActiveChange = {},
            onBack = {},
            onAddClick = {},
            staffToDelete = null,
            onStaffDeleteRequest = {},
            onStaffDeleteConfirm = {},
            onStaffDeleteCancel = {},
            filteredStaff = listOf(Staff("1", "admin@clinic.com", "Admin User", "Admin", 0L)),
            showAddDialog = false,
            onAddDialogDismiss = {},
            onAddStaff = { _, _, _ -> },
            onClearMessages = {}
        )
    }
}
