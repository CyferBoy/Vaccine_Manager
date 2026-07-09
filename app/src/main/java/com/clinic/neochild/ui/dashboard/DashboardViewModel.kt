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
    private val vaccinationRepository: VaccinationRepository
) : ViewModel() {

    private val _lowStockCount = MutableStateFlow(0)
    private val _wasteCount = MutableStateFlow(0)

    val uiState: StateFlow<DashboardUiState> = combine(
        patientRepository.allPatients,
        vaccinationRepository.allVaccinations,
        _lowStockCount,
        _wasteCount
    ) { patients, vaccinations, lowStock, waste ->
        val unsatisfied = ReminderEngine.getUnsatisfiedRequirements(vaccinations)
        
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time
        
        val dueToday = unsatisfied.groupBy { it.patientId + PatientUtils.formatDate(it.dueDate) }
            .count { (_, reqs) -> reqs.any { it.dueDate == todayStart } }

        DashboardUiState(
            patientCount = patients.size,
            lowStockCount = lowStock,
            dueTodayCount = dueToday,
            wasteCount = waste
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    private val listeners = mutableListOf<ListenerRegistration>()

    init {
        startListeners()
    }

    private fun startListeners() {
        // Inventory/Low Stock Listener
        listeners.add(
            firestore.collection("inventory").addSnapshotListener { snapshot, _ ->
                val inventory = snapshot?.documents?.mapNotNull { FirestoreMappers.toVaccine(it) } ?: emptyList()
                _lowStockCount.value = inventory.count { it.stock < 5 }
            }
        )

        // Waste Listener
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
