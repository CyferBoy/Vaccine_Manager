package com.clinic.neochild.ui.vaccine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinic.neochild.data.model.Vaccine
import com.clinic.neochild.data.model.WasteRecord
import com.clinic.neochild.utils.FirestoreMappers
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WasteUiState(
    val wasteRecords: List<WasteRecord> = emptyList(),
    val inventory: List<Vaccine> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class WasteViewModel @Inject constructor(
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _wasteRecords = MutableStateFlow<List<WasteRecord>>(emptyList())
    private val _inventory = MutableStateFlow<List<Vaccine>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    private val _isSaving = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<WasteUiState> = combine(
        _wasteRecords, _inventory, _isLoading, _isSaving, _error
    ) { waste, inv, loading, saving, err ->
        WasteUiState(waste, inv, loading, saving, err)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WasteUiState(isLoading = true))

    private var wasteListener: ListenerRegistration? = null

    init {
        startListeners()
    }

    private fun startListeners() {
        wasteListener = firestore.collection("waste")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    _isLoading.value = false
                    return@addSnapshotListener
                }
                val records = snapshot?.documents?.mapNotNull { FirestoreMappers.toWasteRecord(it) }
                    ?.sortedByDescending { it.dateWasted } ?: emptyList()
                _wasteRecords.value = records
                _isLoading.value = false
            }

        firestore.collection("inventory").get().addOnSuccessListener { result ->
            _inventory.value = result.documents.mapNotNull { FirestoreMappers.toVaccine(it) }
        }
    }

    fun recordWaste(
        vaccineId: String,
        brandName: String,
        batchNumber: String,
        expiryDate: String,
        dateWasted: String,
        reason: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val wasteData = hashMapOf(
                "vaccineId" to vaccineId,
                "brandName" to brandName,
                "batchNumber" to batchNumber,
                "expiryDate" to expiryDate,
                "dateWasted" to dateWasted,
                "reason" to reason,
                "quantity" to 1
            )
            
            try {
                firestore.collection("waste").add(wasteData)
                val currentStock = _inventory.value.find { it.id == vaccineId }?.stock ?: 0
                if (currentStock > 0) {
                    firestore.collection("inventory").document(vaccineId).update("stock", currentStock - 1)
                }
                onSuccess()
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun deleteWaste(id: String) {
        firestore.collection("waste").document(id).delete()
    }

    fun clearError() {
        _error.value = null
    }

    override fun onCleared() {
        super.onCleared()
        wasteListener?.remove()
    }
}
