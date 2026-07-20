package com.clinic.neochild.app

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.clinic.neochild.features.dashboard.AuthViewModel
import com.clinic.neochild.features.dashboard.LoginScreen
import com.clinic.neochild.features.dashboard.ManageStaffScreen
import com.clinic.neochild.features.dashboard.DashboardScreen
import com.clinic.neochild.features.patient.AddPatientScreen
import com.clinic.neochild.features.patient.PatientDetailsScreen
import com.clinic.neochild.features.patient.PatientListScreen
import com.clinic.neochild.features.sync.SyncScreen
import com.clinic.neochild.features.settings.NotificationSettingsScreen
import com.clinic.neochild.features.reminder.DueScreen
import com.clinic.neochild.features.statistics.MonthlyFinanceDetailsScreen
import com.clinic.neochild.features.statistics.StatisticsScreen
import com.clinic.neochild.features.inventory.AddVaccineStockScreen
import com.clinic.neochild.features.search.SearchScreen
import com.clinic.neochild.features.vaccination.AddVaccinationScreen
import com.clinic.neochild.features.inventory.BorrowedScreen
import com.clinic.neochild.features.inventory.VaccineInventoryScreen
import com.clinic.neochild.features.inventory.WasteScreen

@Composable
fun AppNavigation(
    navController: androidx.navigation.NavHostController = rememberNavController()
) {

    val authViewModel: AuthViewModel = hiltViewModel()
    val startDest = if (authViewModel.currentUser != null) Routes.DASHBOARD else Routes.LOGIN

    NavHost(
        navController = navController,
        startDestination = startDest,
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onAddPatient = {
                    navController.navigate(Routes.ADD_PATIENT)
                },
                onPatientList = {
                    navController.navigate(Routes.PATIENT_LIST)
                },
                onAddVaccine = {
                    navController.navigate(Routes.VACCINE_INVENTORY)
                },
                onStatistics = {
                    navController.navigate(Routes.STATISTICS)
                },
                onBorrowed = {
                    navController.navigate(Routes.BORROWED)
                },
                onDue = {
                    navController.navigate(Routes.DUE)
                },
                onWaste = {
                    navController.navigate(Routes.WASTE)
                },
                onManageStaff = {
                    navController.navigate(Routes.MANAGE_STAFF)
                },
                onSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
                onSync = {
                    navController.navigate(Routes.SYNC)
                },
                onSearch = {
                    navController.navigate(Routes.SEARCH)
                },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.DASHBOARD) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.SETTINGS) {
            NotificationSettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SYNC) {
            SyncScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.MANAGE_STAFF) {
            ManageStaffScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onPatientClick = { patientId ->
                    navController.navigate("patient_details/$patientId")
                }
            )
        }

        composable(Routes.ADD_PATIENT) {
            AddPatientScreen(
                onBack = { navController.popBackStack() },
                onNavigateToDetails = { patientId ->
                    navController.navigate("patient_details/$patientId") {
                        popUpTo(Routes.ADD_PATIENT) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Routes.PATIENT_LIST) {
            PatientListScreen(
                onBack = { navController.popBackStack() },
                onAddPatient = { navController.navigate(Routes.ADD_PATIENT) },
                onPatientClick = { patientId ->
                    navController.navigate("patient_details/$patientId")
                },
                onEditPatient = { patientId ->
                    navController.navigate("edit_patient/$patientId")
                }
            )
        }

        composable(
            route = Routes.PATIENT_DETAILS,
            arguments = listOf(navArgument("patientId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getString("patientId") ?: ""
            PatientDetailsScreen(
                patientId = patientId,
                onBack = { navController.popBackStack() },
                onAddVaccine = { id ->
                    navController.navigate("add_vaccine/$id")
                },
                onEditVaccination = { id ->
                    navController.navigate("edit_vaccination/$id")
                }
            )
        }

        composable(
            route = Routes.EDIT_PATIENT,
            arguments = listOf(navArgument("patientId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getString("patientId")
            AddPatientScreen(
                patientId = patientId,
                onBack = { navController.popBackStack() },
                onNavigateToDetails = { id ->
                    navController.navigate("patient_details/$id") {
                        popUpTo(Routes.EDIT_PATIENT) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = Routes.EDIT_VACCINATION,
            arguments = listOf(navArgument("vaccinationId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val vaccinationId = backStackEntry.arguments?.getString("vaccinationId")
            AddVaccinationScreen(
                vaccinationId = vaccinationId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.VACCINE_INVENTORY) {
            VaccineInventoryScreen(
                onBack = { navController.popBackStack() },
                onAddVaccine = { navController.navigate(Routes.ADD_VACCINE_STOCK) },
                onEditVaccine = { id ->
                    navController.navigate("edit_vaccine_stock/$id")
                }
            )
        }

        composable(Routes.STATISTICS) {
            StatisticsScreen(
                onBack = { navController.popBackStack() },
                onMonthClick = { monthKey ->
                    navController.navigate("monthly_finance_details/$monthKey")
                }
            )
        }

        composable(
            route = Routes.MONTHLY_FINANCE_DETAILS,
            arguments = listOf(navArgument("monthKey") { type = NavType.StringType }),
        ) { backStackEntry ->
            val monthKey = backStackEntry.arguments?.getString("monthKey") ?: ""
            // MonthlyFinanceDetailsScreen will be created next
            MonthlyFinanceDetailsScreen(
                monthKey = monthKey,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.BORROWED) {
            BorrowedScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.DUE) {
            DueScreen(
                onBack = { navController.popBackStack() },
                onNavigateToAddVaccination = { patientId, vaccineName ->
                    navController.navigate("add_vaccine_with_details/$patientId/$vaccineName")
                }
            )
        }

        composable(Routes.WASTE) {
            WasteScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.ADD_VACCINE_STOCK) {
            AddVaccineStockScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.EDIT_VACCINE_STOCK,
            arguments = listOf(navArgument("vaccineId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val vaccineId = backStackEntry.arguments?.getString("vaccineId")
            AddVaccineStockScreen(
                batchId = vaccineId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "add_vaccine_with_details/{patientId}/{vaccineName}",
            arguments = listOf(
                navArgument("patientId") { type = NavType.StringType },
                navArgument("vaccineName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getString("patientId") ?: ""
            val vaccineName = backStackEntry.arguments?.getString("vaccineName") ?: ""
            AddVaccinationScreen(
                initialPatientId = patientId,
                initialVaccineName = vaccineName,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.ADD_VACCINE) {
            AddVaccinationScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.ADD_VACCINE_FOR_PATIENT,
            arguments = listOf(navArgument("patientId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getString("patientId") ?: ""
            AddVaccinationScreen(
                initialPatientId = patientId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
