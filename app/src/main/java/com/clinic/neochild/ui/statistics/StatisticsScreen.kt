package com.clinic.neochild.ui.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clinic.neochild.data.model.Patient
import com.clinic.neochild.data.model.Vaccination
import com.clinic.neochild.utils.FirestoreMappers
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

/**
 * Main Statistics Dashboard Screen that hosts different analytic tabs via a Sidebar (Navigation Drawer).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(onBack: () -> Unit = {}, onMonthClick: (String) -> Unit = {}) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Overview", "Patients", "Vaccinations", "Finance")
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var patients by remember { mutableStateOf<List<Patient>>(emptyList()) }
    var vaccinations by remember { mutableStateOf<List<Vaccination>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val db = FirebaseFirestore.getInstance()

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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Statistics Menu",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                tabs.forEachIndexed { index, title ->
                    NavigationDrawerItem(
                        label = { Text(title) },
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            scope.launch { drawerState.close() }
                        },
                        icon = {
                            val icon = when (title) {
                                "Overview" -> Icons.Default.Dashboard
                                "Patients" -> Icons.Default.People
                                "Vaccinations" -> Icons.Default.Vaccines
                                "Finance" -> Icons.Default.Payments
                                else -> Icons.Default.BarChart
                            }
                            Icon(icon, contentDescription = null)
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                NavigationDrawerItem(
                    label = { Text("Back to Dashboard") },
                    selected = false,
                    onClick = { onBack() },
                    icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Statistics", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = tabs[selectedTab],
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Open Menu", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            },
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    when (selectedTab) {
                        0 -> OverviewTab(patients, vaccinations)
                        1 -> PatientsTab(patients)
                        2 -> VaccinationsTab(vaccinations)
                        3 -> FinanceTab(vaccinations, onMonthClick)
                    }
                }
            }
        }
    }
}
