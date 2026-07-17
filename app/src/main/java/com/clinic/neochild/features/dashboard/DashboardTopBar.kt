package com.clinic.neochild.features.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clinic.neochild.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardTopBar(
    userName: String,
    isAdmin: Boolean,
    onManageStaff: () -> Unit,
    onSettings: () -> Unit,
    onSync: () -> Unit,
    onLogout: () -> Unit
) {
    var profileMenuExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text("Neo Child Clinic", fontWeight = FontWeight.SemiBold) },
        actions = {
            Box {
                IconButton(onClick = { profileMenuExpanded = true }) {
                    Surface(
                        modifier = Modifier.size(32.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = "Profile",
                            modifier = Modifier.padding(4.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                DropdownMenu(
                    expanded = profileMenuExpanded,
                    onDismissRequest = { profileMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { 
                            Column {
                                Text(text = userName, fontWeight = FontWeight.Bold)
                                Text("Profile", style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        onClick = { profileMenuExpanded = false },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Cloud Sync") },
                        onClick = {
                            profileMenuExpanded = false
                            onSync()
                        },
                        leadingIcon = { Icon(Icons.Default.Sync, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Notification Settings") },
                        onClick = {
                            profileMenuExpanded = false
                            onSettings()
                        },
                        leadingIcon = { Icon(Icons.Default.Notifications, contentDescription = null) }
                    )
                    HorizontalDivider()
                    if (isAdmin) {
                        DropdownMenuItem(
                            text = { Text("Manage Staff") },
                            onClick = {
                                profileMenuExpanded = false
                                onManageStaff()
                            },
                            leadingIcon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null) }
                        )
                        HorizontalDivider()
                    }
                    DropdownMenuItem(
                        text = { Text("Logout") },
                        onClick = {
                            profileMenuExpanded = false
                            onLogout()
                        },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout") }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            actionIconContentColor = MaterialTheme.colorScheme.onBackground
        )
    )
}

@Composable
fun ClinicLogo(isWideScreen: Boolean) {
    val logoSize = if (isWideScreen) 200.dp else 160.dp
    Image(
        painter = painterResource(id = R.drawable.logo),
        contentDescription = "Clinic Logo",
        modifier = Modifier.size(logoSize)
    )
}
