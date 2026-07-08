package com.clinic.neochild.ui.auth

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.clinic.neochild.data.model.Staff
import com.clinic.neochild.utils.FirestoreMappers
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.clinic.neochild.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import android.util.Log

class AdminViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _error = mutableStateOf<String?>(null)
    val error: State<String?> = _error

    private val _success = mutableStateOf<String?>(null)
    val success: State<String?> = _success

    private val _staffList = MutableStateFlow<List<Staff>>(emptyList())
    val staffList: StateFlow<List<Staff>> = _staffList

    init {
        fetchStaff()
    }

    fun fetchStaff() {
        _isLoading.value = true
        db.collection("staff")
            .get()
            .addOnSuccessListener { result ->
                val list = result.documents.mapNotNull { FirestoreMappers.toStaff(it) }
                _staffList.value = list
                _isLoading.value = false
            }
            .addOnFailureListener {
                _error.value = it.message
                _isLoading.value = false
            }
    }

    /**
     * Creates a new staff account without logging out the current admin user.
     * This uses a secondary FirebaseApp instance to perform registration.
     */
    fun createStaffAccount(name: String, email: String, pass: String) {
        if (name.isBlank() || email.isBlank() || pass.isBlank()) {
            _error.value = "Please fill all fields"
            return
        }

        _isLoading.value = true
        _error.value = null
        _success.value = null

        // Get the current FirebaseApp options
        val currentApp = FirebaseApp.getInstance()
        val options = currentApp.options

        // Use a secondary app instance for background registration
        val secondaryAppName = "SecondaryRegisterApp"
        val secondaryApp = try {
            FirebaseApp.initializeApp(currentApp.applicationContext, options, secondaryAppName)
        } catch (e: Exception) {
            FirebaseApp.getInstance(secondaryAppName)
        }

        // Initialize App Check for secondary app
        try {
            val appCheck = FirebaseAppCheck.getInstance(secondaryApp)
            // Explicitly install Play Integrity for the secondary app
            val factory = PlayIntegrityAppCheckProviderFactory.getInstance()
            appCheck.installAppCheckProviderFactory(factory)
            
            // Log for verification
            Log.d("AdminViewModel", "Play Integrity installed for secondary app: ${secondaryApp.name}")
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

                // Save to Firestore using the DEFAULT app instance
                // The Admin is authenticated on the default app, so this should use the main App Check token
                db.collection("staff").document(uid).set(staff)
                    .addOnSuccessListener {
                        _isLoading.value = false
                        _success.value = "Staff account created successfully!"
                        fetchStaff()
                        // Sign out of the secondary instance
                        secondaryAuth.signOut()
                    }
                    .addOnFailureListener {
                        _isLoading.value = false
                        _error.value = "Auth created but Firestore failed: ${it.message}"
                    }
            }
            .addOnFailureListener {
                _isLoading.value = false
                _error.value = it.message
            }
    }

    fun deleteStaff(staffId: String) {
        _isLoading.value = true
        db.collection("staff").document(staffId).delete()
            .addOnSuccessListener {
                _isLoading.value = false
                _success.value = "Staff deleted successfully"
                fetchStaff()
            }
            .addOnFailureListener {
                _isLoading.value = false
                _error.value = it.message
            }
    }

    fun clearMessages() {
        _error.value = null
        _success.value = null
    }
}
