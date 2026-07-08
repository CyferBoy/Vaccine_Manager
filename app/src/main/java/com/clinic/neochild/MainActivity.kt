package com.clinic.neochild

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.compose.rememberNavController
import com.clinic.neochild.navigation.AppNavigation
import com.clinic.neochild.navigation.Routes
import com.clinic.neochild.ui.theme.NeoChildTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : ComponentActivity() {
    private var openDueTab by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        
        enableEdgeToEdge()
        setContent {
            NeoChildTheme {
                val navController = rememberNavController()
                
                // Permission request for Android 13+
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        // Permission granted
                        fetchAndStoreFcmToken()
                    }
                }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        fetchAndStoreFcmToken()
                    }
                }

                LaunchedEffect(openDueTab) {
                    if (openDueTab) {
                        navController.navigate(Routes.DUE)
                        openDueTab = false
                    }
                }

                AppNavigation(navController = navController)
            }
        }
    }

    private fun fetchAndStoreFcmToken() {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            val db = FirebaseFirestore.getInstance()
            db.collection("users").document(currentUser.uid)
                .update("fcmToken", token)
                .addOnFailureListener {
                    val data = hashMapOf("fcmToken" to token, "email" to currentUser.email)
                    db.collection("users").document(currentUser.uid).set(data)
                }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        // Glance action parameters are passed as extras with keys prefixed or mapped
        // For actionStartActivity<MainActivity>(actionParametersOf(key to value))
        // The key is usually the string name used in ActionParameters.Key
        if (intent?.extras?.containsKey("OPEN_DUE_TAB") == true) {
            openDueTab = intent.getBooleanExtra("OPEN_DUE_TAB", false)
        }
    }
}
