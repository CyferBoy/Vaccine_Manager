package com.clinic.neochild.ui.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import com.clinic.neochild.domain.model.Staff
import com.clinic.neochild.data.mapper.FirestoreMappers
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class AdminUiState(
    val staffList: List<Staff> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: String? = null
)

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val db: FirebaseFirestore
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    init {
        fetchStaff()
    }

    fun fetchStaff() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        db.collection("staff")
            .get()
            .addOnSuccessListener { result ->
                val list = result.documents.mapNotNull { FirestoreMappers.toStaff(it) }
                _uiState.value = _uiState.value.copy(staffList = list, isLoading = false)
            }
            .addOnFailureListener {
                _uiState.value = _uiState.value.copy(error = it.message, isLoading = false)
            }
    }

    fun createStaffAccount(name: String, email: String, pass: String) {
        if (name.isBlank() || email.isBlank() || pass.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Please fill all fields")
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, error = null, success = null)

        val currentApp = FirebaseApp.getInstance()
        val options = currentApp.options
        val secondaryAppName = "SecondaryRegisterApp"
        val secondaryApp = try {
            FirebaseApp.initializeApp(currentApp.applicationContext, options, secondaryAppName)
        } catch (e: Exception) {
            FirebaseApp.getInstance(secondaryAppName)
        }

        try {
            val appCheck = FirebaseAppCheck.getInstance(secondaryApp)
            val factory = PlayIntegrityAppCheckProviderFactory.getInstance()
            appCheck.installAppCheckProviderFactory(factory)
        } catch (e: Exception) {
            Log.e("AdminViewModel", "App Check initialization failed for secondary app: ${e.message}")
        }

        val secondaryAuth = FirebaseAuth.getInstance(secondaryApp)

        secondaryAuth.createUserWithEmailAndPassword(email, pass)
            .addOnSuccessListener { authResult ->
                val uid = authResult.user?.uid ?: ""
                val staff = Staff(
                    id = uid,
                    name = name,
                    email = email,
                    role = "Staff",
                    createdAt = System.currentTimeMillis()
                )

                db.collection("staff").document(uid).set(staff)
                    .addOnSuccessListener {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            success = "Staff account created successfully!"
                        )
                        fetchStaff()
                        secondaryAuth.signOut()
                    }
                    .addOnFailureListener {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "Auth created but Firestore failed: ${it.message}"
                        )
                    }
            }
            .addOnFailureListener {
                _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
            }
    }

    fun deleteStaff(staffId: String) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        db.collection("staff").document(staffId).delete()
            .addOnSuccessListener {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    success = "Staff deleted successfully"
                )
                fetchStaff()
            }
            .addOnFailureListener {
                _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
            }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, success = null)
    }
}
