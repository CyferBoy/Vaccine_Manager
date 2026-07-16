package com.clinic.neochild.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinic.neochild.data.model.Vaccine
import com.clinic.neochild.data.model.Vaccination
import com.clinic.neochild.domain.repository.VaccinationRepository
import com.clinic.neochild.domain.repository.PatientRepository
import com.clinic.neochild.utils.Constants
import com.clinic.neochild.utils.FirestoreMappers
import com.clinic.neochild.utils.ReminderEngine
import com.clinic.neochild.utils.PatientUtils
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class DashboardUiState(
    val patientCount: Int = 0,
    val lowStockCount: Int = 0,
    val dueTodayCount: Int = 0,
    val wasteCount: Int = 0
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val patientRepository: PatientRepository,
    private val reminderRepository: com.clinic.neochild.domain.repository.ReminderRepository,
    private val getClinicStatsUseCase: com.clinic.neochild.domain.usecase.statistics.GetClinicStatsUseCase,
    private val inventoryRepository: com.clinic.neochild.domain.repository.InventoryRepository
) : ViewModel() {

    private val _wasteCount = MutableStateFlow(0)

    val uiState: StateFlow<DashboardUiState> = combine(
        patientRepository.allPatients,
        getClinicStatsUseCase(),
        inventoryRepository.getInventoryItems(),
        _wasteCount
    ) { patients, stats, inventory, waste ->
        DashboardUiState(
            patientCount = patients.size,
            lowStockCount = inventory.count { it.stock <= it.threshold },
            dueTodayCount = stats.dueToday,
            wasteCount = waste
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    private val listeners = mutableListOf<ListenerRegistration>()

    init {
        // Waste Listener (Keeping for real-time until repo supports it)
        listeners.add(
            firestore.collection("waste").addSnapshotListener { snapshot, _ ->
                _wasteCount.value = snapshot?.size() ?: 0
            }
        )
    }

    override fun onCleared() {
        super.onCleared()
        listeners.forEach { it.remove() }
    }
}
