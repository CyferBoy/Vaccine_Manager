package com.clinic.neochild.ui.dashboard

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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clinic.neochild.R
import com.clinic.neochild.ui.auth.AuthViewModel
import com.clinic.neochild.ui.theme.*
import com.clinic.neochild.ui.viewmodel.PatientViewModel
import com.clinic.neochild.utils.Constants
import com.clinic.neochild.utils.FirestoreMappers
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onAddPatient: () -> Unit = {},
    onPatientList: () -> Unit = {},
    onAddVaccine: () -> Unit = {}, // Inventory
    onStatistics: () -> Unit = {},
    onBorrowed: () -> Unit = {},
    onDue: () -> Unit = {},
    onWaste: () -> Unit = {},
    onManageStaff: () -> Unit = {},
    onLogout: () -> Unit = {},
    authViewModel: AuthViewModel = viewModel(),
    patientViewModel: PatientViewModel = viewModel()
) {
    val db = FirebaseFirestore.getInstance()
    val isAdmin = Constants.ADMIN_EMAILS.contains(authViewModel.currentUser?.email)
    var patientCount by remember { mutableIntStateOf(0) }
    var lowStockCount by remember { mutableIntStateOf(0) }
    var dueTodayCount by remember { mutableIntStateOf(0) }
    var wasteCount by remember { mutableIntStateOf(0) }
    var profileMenuExpanded by remember { mutableStateOf(false) }
    
    val allPatients by patientViewModel.allPatients.collectAsState()
    val allVaccinations by patientViewModel.allVaccinations.collectAsState()
    
    LaunchedEffect(Unit) {
        db.collection("patients").addSnapshotListener { snapshot, _ ->
            patientCount = snapshot?.size() ?: 0
        }
        db.collection("inventory").addSnapshotListener { snapshot, _ ->
            val inventory = snapshot?.documents?.mapNotNull { FirestoreMappers.toVaccine(it) } ?: emptyList()
            lowStockCount = inventory.count { it.stock < 5 }
        }
        db.collection("waste").addSnapshotListener { snapshot, _ ->
            wasteCount = snapshot?.size() ?: 0
        }
        db.collection("vaccinations").addSnapshotListener { snapshot, _ ->
            val vaccinations = snapshot?.documents?.mapNotNull { FirestoreMappers.toVaccination(it) } ?: emptyList()
            val today = SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(Date())
            dueTodayCount = vaccinations.count { !it.isDone && it.nextDueDate == today }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { 
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
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
                                            Text(
                                                text = authViewModel.currentUser?.email?.split("@")?.firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "User",
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text("Profile", style = MaterialTheme.typography.bodySmall)
                                        }
                                    },
                                    onClick = { 
                                        profileMenuExpanded = false
                                        // Handle Profile click if needed
                                    },
                                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
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
                                        authViewModel.logout()
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
        ) { paddingValues ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp)
            ) {
                val screenWidth = maxWidth
                val isWideScreen = screenWidth > 600.dp
                
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(10.dp))

                    // Logo
                    val logoSize = if (isWideScreen) 200.dp else 160.dp
                    Image(
                        painter = painterResource(id = R.drawable.logo),
                        contentDescription = "Clinic Logo",
                        modifier = Modifier.size(logoSize)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Dashboard",
                        style = if (isWideScreen) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    if (isWideScreen) {
                        // Wide Screen Grid: 4 items in a row or 2x2 grid
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
                                    badge = patientCount.toString(),
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
                                    badge = if (lowStockCount > 0) "$lowStockCount Low" else null,
                                    height = 160.dp,
                                    modifier = Modifier.weight(1f),
                                    onClick = onAddVaccine
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
                        // Standard Phone Grid (Vertical staggered)
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
                                    badge = patientCount.toString(),
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
                                    badge = if (lowStockCount > 0) "$lowStockCount Low" else null,
                                    height = 200.dp,
                                    onClick = onAddVaccine
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Small buttons row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DashboardCardSmall(
                            title = "Borrowed",
                            icon = Icons.Default.CompareArrows,
                            containerColor = if (isSystemInDarkTheme()) Color(0xFF37474F) else Color(0xFFECEFF1),
                            contentColor = if (isSystemInDarkTheme()) Color(0xFFCFD8DC) else Color(0xFF263238),
                            modifier = Modifier.weight(1f),
                            onClick = onBorrowed
                        )
                        DashboardCardSmall(
                            title = "Due",
                            icon = Icons.Default.EventNote,
                            containerColor = if (isSystemInDarkTheme()) DarkBrownContainer else Color(0xFFEFEBE9),
                            contentColor = if (isSystemInDarkTheme()) DarkOnBrownContainer else Color(0xFF3E2723),
                            badge = if (dueTodayCount > 0) dueTodayCount.toString() else null,
                            modifier = Modifier.weight(1f),
                            onClick = onDue
                        )
                        DashboardCardSmall(
                            title = "Waste",
                            icon = Icons.Default.DeleteSweep,
                            containerColor = if (isSystemInDarkTheme()) DarkRedContainer else Color(0xFFFBE9E7),
                            contentColor = if (isSystemInDarkTheme()) DarkOnRedContainer else Color(0xFFBF360C),
                            badge = if (wasteCount > 0) wasteCount.toString() else null,
                            modifier = Modifier.weight(1f),
                            onClick = onWaste
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }
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
