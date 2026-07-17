package com.clinic.neochild.features.dashboard

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.runtime.State
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _error = mutableStateOf<String?>(null)
    val error: State<String?> = _error

    val currentUser = auth.currentUser

    fun login(email: String, pass: String, onSuccess: () -> Unit) {
        if (email.isBlank() || pass.isBlank()) {
            _error.value = "Please fill all fields"
            return
        }
        _isLoading.value = true
        _error.value = null
        auth.signInWithEmailAndPassword(email, pass)
            .addOnSuccessListener {
                _isLoading.value = false
                onSuccess()
            }
            .addOnFailureListener {
                _isLoading.value = false
                _error.value = it.message
            }
    }

    fun logout() {
        auth.signOut()
    }
}
