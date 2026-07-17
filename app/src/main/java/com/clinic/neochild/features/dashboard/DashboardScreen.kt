package com.clinic.neochild.features.dashboard

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.clinic.neochild.R
import com.clinic.neochild.features.dashboard.AuthViewModel
import com.clinic.neochild.core.designsystem.*
import com.clinic.neochild.core.constants.Constants

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onAddPatient: () -> Unit = {},
    onPatientList: () -> Unit = {},
    onAddVaccine: () -> Unit = {},
    onStatistics: () -> Unit = {},
    onBorrowed: () -> Unit = {},
    onDue: () -> Unit = {},
    onWaste: () -> Unit = {},
    onManageStaff: () -> Unit = {},
    onLogout: () -> Unit = {},
    onSettings: () -> Unit = {},
    onSync: () -> Unit = {},
    authViewModel: AuthViewModel = hiltViewModel(),
    dashboardViewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by dashboardViewModel.uiState.collectAsState()
    val isAdmin = Constants.ADMIN_EMAILS.contains(authViewModel.currentUser?.email)

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { 
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                DashboardTopBar(
                    userName = authViewModel.currentUser?.email?.split("@")?.firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "User",
                    isAdmin = isAdmin,
                    onManageStaff = onManageStaff,
                    onSettings = onSettings,
                    onSync = onSync,
                    onLogout = {
                        authViewModel.logout()
                        onLogout()
                    }
                )
            }
        ) { paddingValues ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp)
            ) {
                val isWideScreen = maxWidth > 600.dp
                
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(10.dp))
                    ClinicLogo(isWideScreen)
                    
                    Text(
                        text = "Dashboard",
                        style = if (isWideScreen) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    DashboardMainGrid(
                        isWideScreen = isWideScreen,
                        uiState = uiState,
                        onPatientList = onPatientList,
                        onAddPatient = onAddPatient,
                        onInventory = onAddVaccine,
                        onStatistics = onStatistics
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    DashboardSmallActionsRow(
                        uiState = uiState,
                        onBorrowed = onBorrowed,
                        onDue = onDue,
                        onWaste = onWaste
                    )
                    
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardTopBar(
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
private fun ClinicLogo(isWideScreen: Boolean) {
    val logoSize = if (isWideScreen) 200.dp else 160.dp
    Image(
        painter = painterResource(id = R.drawable.logo),
        contentDescription = "Clinic Logo",
        modifier = Modifier.size(logoSize)
    )
}

@Composable
private fun DashboardMainGrid(
    isWideScreen: Boolean,
    uiState: DashboardUiState,
    onPatientList: () -> Unit,
    onAddPatient: () -> Unit,
    onInventory: () -> Unit,
    onStatistics: () -> Unit
) {
    if (isWideScreen) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DashboardCard(
                    title = "Patient List",
                    icon = Icons.AutoMirrored.Filled.List,
                    containerColor = if (isSystemInDarkTheme()) DarkBlueContainer else Color(0xFFE3F2FD),
                    contentColor = if (isSystemInDarkTheme()) DarkOnBlueContainer else Color(0xFF004977),
                    badge = uiState.patientCount.toString(),
                    height = 160.dp,
                    modifier = Modifier.weight(1f),
                    onClick = onPatientList
                )
                DashboardCard(
                    title = "Add Patient",
                    icon = Icons.Default.Add,
                    containerColor = if (isSystemInDarkTheme()) DarkGreenContainer else Color(0xFFE8F5E9),
                    contentColor = if (isSystemInDarkTheme()) DarkOnGreenContainer else Color(0xFF1B5E20),
                    height = 160.dp,
                    modifier = Modifier.weight(1f),
                    onClick = onAddPatient
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DashboardCard(
                    title = "Inventory",
                    icon = Icons.Default.ShoppingCart,
                    containerColor = if (isSystemInDarkTheme()) DarkOrangeContainer else Color(0xFFFFF3E0),
                    contentColor = if (isSystemInDarkTheme()) DarkOnOrangeContainer else Color(0xFFE65100),
                    badge = if (uiState.lowStockCount > 0) "${uiState.lowStockCount} Low" else null,
                    height = 160.dp,
                    modifier = Modifier.weight(1f),
                    onClick = onInventory
                )
                DashboardCard(
                    title = "Statistics",
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    containerColor = if (isSystemInDarkTheme()) DarkPurpleContainer else Color(0xFFF3E5F5),
                    contentColor = if (isSystemInDarkTheme()) DarkOnPurpleContainer else Color(0xFF4A148C),
                    height = 160.dp,
                    modifier = Modifier.weight(1f),
                    onClick = onStatistics
                )
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DashboardCard(
                    title = "Patient List",
                    icon = Icons.AutoMirrored.Filled.List,
                    containerColor = if (isSystemInDarkTheme()) DarkBlueContainer else Color(0xFFE3F2FD),
                    contentColor = if (isSystemInDarkTheme()) DarkOnBlueContainer else Color(0xFF004977),
                    badge = uiState.patientCount.toString(),
                    height = 200.dp,
                    onClick = onPatientList
                )
                DashboardCard(
                    title = "Statistics",
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    containerColor = if (isSystemInDarkTheme()) DarkPurpleContainer else Color(0xFFF3E5F5),
                    contentColor = if (isSystemInDarkTheme()) DarkOnPurpleContainer else Color(0xFF4A148C),
                    height = 140.dp,
                    onClick = onStatistics
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DashboardCard(
                    title = "Add Patient",
                    icon = Icons.Default.Add,
                    containerColor = if (isSystemInDarkTheme()) DarkGreenContainer else Color(0xFFE8F5E9),
                    contentColor = if (isSystemInDarkTheme()) DarkOnGreenContainer else Color(0xFF1B5E20),
                    height = 140.dp,
                    onClick = onAddPatient
                )
                DashboardCard(
                    title = "Inventory",
                    icon = Icons.Default.ShoppingCart,
                    containerColor = if (isSystemInDarkTheme()) DarkOrangeContainer else Color(0xFFFFF3E0),
                    contentColor = if (isSystemInDarkTheme()) DarkOnOrangeContainer else Color(0xFFE65100),
                    badge = if (uiState.lowStockCount > 0) "${uiState.lowStockCount} Low" else null,
                    height = 200.dp,
                    onClick = onInventory
                )
            }
        }
    }
}

@Composable
private fun DashboardSmallActionsRow(
    uiState: DashboardUiState,
    onBorrowed: () -> Unit,
    onDue: () -> Unit,
    onWaste: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DashboardCardSmall(
            title = "Borrowed",
            icon = Icons.Default.SwapHoriz,
            containerColor = if (isSystemInDarkTheme()) Color(0xFF004D40) else Color(0xFFE0F2F1),
            contentColor = if (isSystemInDarkTheme()) Color(0xFF80CBC4) else Color(0xFF00695C),
            modifier = Modifier.weight(1f),
            onClick = onBorrowed
        )
@Suppress("DEPRECATION")
        DashboardCardSmall(
            title = "Due",
            icon = Icons.Default.EventNote,
            containerColor = if (isSystemInDarkTheme()) DarkBrownContainer else Color(0xFFEFEBE9),
            contentColor = if (isSystemInDarkTheme()) DarkOnBrownContainer else Color(0xFF3E2723),
            badge = if (uiState.dueTodayCount > 0) uiState.dueTodayCount.toString() else null,
            modifier = Modifier.weight(1f),
            onClick = onDue
        )
        DashboardCardSmall(
            title = "Waste",
            icon = Icons.Default.DeleteSweep,
            containerColor = if (isSystemInDarkTheme()) DarkRedContainer else Color(0xFFFBE9E7),
            contentColor = if (isSystemInDarkTheme()) DarkOnRedContainer else Color(0xFFBF360C),
            badge = if (uiState.wasteCount > 0) uiState.wasteCount.toString() else null,
            modifier = Modifier.weight(1f),
            onClick = onWaste
        )
    }
}

@Composable
fun DashboardCardSmall(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color = MaterialTheme.colorScheme.onErrorContainer,
    badge: String? = null,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(100.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (badge != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    color = contentColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = badge,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }
            Column(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = contentColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }
        }
    }
}

@Composable
fun DashboardCard(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color = MaterialTheme.colorScheme.onErrorContainer,
    badge: String? = null,
    height: Dp = 160.dp,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(height).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (badge != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    color = contentColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = badge,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = contentColor
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    fontSize = 15.sp
                )
            }
        }
    }
}
