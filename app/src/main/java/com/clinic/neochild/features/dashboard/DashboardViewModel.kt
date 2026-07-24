package com.clinic.neochild.features.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinic.neochild.data.remote.mapper.FirestoreMappers
import com.clinic.neochild.domain.model.ClinicStats
import com.clinic.neochild.domain.model.Staff
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
    val userName: String = "User",
    val staff: Staff? = null
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

    private val _staff = MutableStateFlow<Staff?>(null)

    init {
        fetchStaffProfile()
    }

    private fun fetchStaffProfile() {
        val currentUser = auth.currentUser ?: return
        
        db.collection("staff").document(currentUser.uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    _staff.value = FirestoreMappers.toStaff(doc)
                } else {
                    // Fallback staff object
                    _staff.value = Staff(
                        id = currentUser.uid,
                        email = currentUser.email ?: "",
                        name = currentUser.displayName ?: currentUser.email?.substringBefore("@") ?: "User",
                        role = "User",
                        createdAt = currentUser.metadata?.creationTimestamp ?: 0L
                    )
                }
            }
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        patientRepository.getPatientCount(),
        getClinicStatsUseCase(),
        wasteRepository.getWasteCount(),
        _staff
    ) { patientCount, stats, wasteCount, staff ->
        DashboardUiState(
            patientCount = patientCount,
            lowStockCount = stats.lowStockCount,
            dueTodayCount = stats.dueToday,
            wasteCount = wasteCount,
            userName = staff?.name ?: "User",
            staff = staff
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())
}
