package com.clinic.neochild.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinic.neochild.data.model.Vaccine
import com.clinic.neochild.data.model.Vaccination
import com.clinic.neochild.utils.Constants
import com.clinic.neochild.utils.FirestoreMappers
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val listeners = mutableListOf<ListenerRegistration>()

    init {
        startListeners()
    }

    private fun startListeners() {
        // Patients Listener
        listeners.add(
            firestore.collection("patients").addSnapshotListener { snapshot, _ ->
                _uiState.value = _uiState.value.copy(patientCount = snapshot?.size() ?: 0)
            }
        )

        // Inventory/Low Stock Listener
        listeners.add(
            firestore.collection("inventory").addSnapshotListener { snapshot, _ ->
                val inventory = snapshot?.documents?.mapNotNull { FirestoreMappers.toVaccine(it) } ?: emptyList()
                val lowStock = inventory.count { it.stock < 5 }
                _uiState.value = _uiState.value.copy(lowStockCount = lowStock)
            }
        )

        // Waste Listener
        listeners.add(
            firestore.collection("waste").addSnapshotListener { snapshot, _ ->
                _uiState.value = _uiState.value.copy(wasteCount = snapshot?.size() ?: 0)
            }
        )

        // Due Today Listener
        listeners.add(
            firestore.collection("vaccinations").addSnapshotListener { snapshot, _ ->
                val vaccinations = snapshot?.documents?.mapNotNull { FirestoreMappers.toVaccination(it) } ?: emptyList()
                val today = SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(Date())
                val dueToday = vaccinations.count { !it.isDone && it.nextDueDate == today }
                _uiState.value = _uiState.value.copy(dueTodayCount = dueToday)
            }
        )
    }

    override fun onCleared() {
        super.onCleared()
        listeners.forEach { it.remove() }
    }
}
