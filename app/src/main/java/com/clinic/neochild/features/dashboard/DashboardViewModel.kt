package com.clinic.neochild.features.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinic.neochild.domain.model.ClinicStats
import com.clinic.neochild.domain.repository.PatientRepository
import com.clinic.neochild.domain.repository.WasteRepository
import com.clinic.neochild.domain.usecase.statistics.GetClinicStatsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class DashboardUiState(
    val patientCount: Int = 0,
    val lowStockCount: Int = 0,
    val dueTodayCount: Int = 0,
    val wasteCount: Int = 0,
    val userName: String = "User"
)

/**
 * Orchestrates Dashboard data using unified data streams.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val patientRepository: PatientRepository,
    private val getClinicStatsUseCase: GetClinicStatsUseCase,
    private val wasteRepository: WasteRepository,
    private val auth: com.google.firebase.auth.FirebaseAuth,
    private val db: com.google.firebase.firestore.FirebaseFirestore
) : ViewModel() {

    private val _userName = MutableStateFlow("User")

    init {
        fetchUserName()
    }

    private fun fetchUserName() {
        val currentUser = auth.currentUser ?: return
        val email = currentUser.email ?: ""
        val fallbackName = email.split("@").firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "User"
        _userName.value = fallbackName

        db.collection("staff").document(currentUser.uid).get()
            .addOnSuccessListener { doc ->
                val name = doc.getString("name")
                if (!name.isNullOrBlank()) {
                    _userName.value = name
                }
            }
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        patientRepository.getPatientCount(),
        getClinicStatsUseCase(),
        wasteRepository.getWasteCount(),
        _userName
    ) { patientCount, stats, wasteCount, userName ->
        DashboardUiState(
            patientCount = patientCount,
            lowStockCount = stats.lowStockCount,
            dueTodayCount = stats.dueToday,
            wasteCount = wasteCount,
            userName = userName
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())
}
