package com.clinic.neochild.features.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinic.neochild.core.model.BorrowedVaccine
import com.clinic.neochild.domain.model.InventoryItem
import com.clinic.neochild.domain.model.InventoryTransactionType
import com.clinic.neochild.domain.repository.InventoryRepository
import com.clinic.neochild.data.remote.mapper.FirestoreMappers
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class BorrowedUiState(
    val borrowedList: List<BorrowedVaccine> = emptyList(),
    val inventory: List<InventoryItem> = emptyList(),
    val isLoading: Boolean = false,
    val selectedTab: Int = 0
)

@HiltViewModel
class BorrowedViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val inventoryRepository: InventoryRepository
) : ViewModel() {

    private val _borrowedList = MutableStateFlow<List<BorrowedVaccine>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    private val _selectedTab = MutableStateFlow(0)

    val uiState: StateFlow<BorrowedUiState> = combine(
        _borrowedList, 
        inventoryRepository.getInventoryItems(),
        _isLoading, 
        _selectedTab
    ) { borrowed, inv, loading, tab ->
        BorrowedUiState(borrowed, inv, loading, tab)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BorrowedUiState(isLoading = true))

    private var borrowedListener: ListenerRegistration? = null

    init {
        startListeners()
    }

    private fun startListeners() {
        borrowedListener = firestore.collection("borrowed")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    _isLoading.value = false
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { FirestoreMappers.toBorrowedVaccine(it) } ?: emptyList()
                val fifteenDaysAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -15) }.time
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
                
                val filtered = list.filter { item ->
                    if (!item.isReturned || item.returnedDate == null) true
                    else {
                        val returnedDate = try { sdf.parse(item.returnedDate) } catch (ex: Exception) { null }
                        returnedDate == null || returnedDate.after(fifteenDaysAgo)
                    }
                }.sortedByDescending { it.borrowedDate }
                
                _borrowedList.value = filtered
                _isLoading.value = false
            }
    }

    fun updateTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun saveBorrowedItem(item: BorrowedVaccine) {
        viewModelScope.launch {
            val user = FirebaseAuth.getInstance().currentUser?.email ?: "Unknown"
            
            if (item.id.isEmpty()) {
                // New Borrow - Deduct from inventory
                // Find matching vaccine by name (fallback to FEFO)
                inventoryRepository.getInventoryItems().first().find { it.brandName.equals(item.vaccineName, true) }?.let { invItem ->
                    inventoryRepository.deductStock(
                        vaccineId = invItem.id,
                        quantity = 1,
                        user = user,
                        transactionType = InventoryTransactionType.OTHER // Could use a specific one if added
                    )
                }
                firestore.collection("borrowed").add(item)
            } else {
                firestore.collection("borrowed").document(item.id).set(item)
            }
        }
    }

    fun markAsReturned(item: BorrowedVaccine) {
        viewModelScope.launch {
            val user = FirebaseAuth.getInstance().currentUser?.email ?: "Unknown"
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
            val updated = item.copy(isReturned = true, returnedDate = sdf.format(Date()))
            
            // Returned - Restore stock
            inventoryRepository.getInventoryItems().first().find { it.brandName.equals(item.vaccineName, true) }?.let { invItem ->
                // Try to find the batch if batchNumber matches
                val batchId = invItem.batches.find { it.batchNumber == item.batchNumber }?.batchId
                if (batchId != null) {
                    inventoryRepository.addStockToBatch(batchId, 1, user, InventoryTransactionType.RETURN)
                }
            }
            
            firestore.collection("borrowed").document(item.id).set(updated)
        }
    }

    fun deleteBorrowedItem(id: String) {
        firestore.collection("borrowed").document(id).delete()
    }

    override fun onCleared() {
        super.onCleared()
        borrowedListener?.remove()
    }
}
