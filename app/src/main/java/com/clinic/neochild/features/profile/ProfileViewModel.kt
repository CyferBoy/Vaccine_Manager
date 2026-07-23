package com.clinic.neochild.features.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinic.neochild.data.remote.mapper.FirestoreMappers
import com.clinic.neochild.domain.model.Staff
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class ProfileUiState(
    val staff: Staff? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: String? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        val currentUser = auth.currentUser ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val doc = db.collection("staff").document(currentUser.uid).get().await()
                if (doc.exists()) {
                    val staff = FirestoreMappers.toStaff(doc)
                    _uiState.value = _uiState.value.copy(staff = staff, isLoading = false)
                } else {
                    // Fallback to basic auth info if staff profile doesn't exist yet
                    val staff = Staff(
                        id = currentUser.uid,
                        email = currentUser.email ?: "",
                        name = currentUser.displayName ?: currentUser.email?.split("@")?.firstOrNull() ?: "User",
                        role = "User",
                        createdAt = currentUser.metadata?.creationTimestamp ?: 0L
                    )
                    _uiState.value = _uiState.value.copy(staff = staff, isLoading = false)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message, isLoading = false)
            }
        }
    }

    fun updateName(newName: String) {
        if (newName.isBlank()) return
        val currentUser = auth.currentUser ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, success = null)
            try {
                db.collection("staff").document(currentUser.uid).update("name", newName).await()
                db.collection("users").document(currentUser.uid).update("name", newName).await()
                
                _uiState.value = _uiState.value.copy(
                    staff = _uiState.value.staff?.copy(name = newName),
                    isLoading = false,
                    success = "Name updated successfully"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message, isLoading = false)
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, success = null)
    }
}
