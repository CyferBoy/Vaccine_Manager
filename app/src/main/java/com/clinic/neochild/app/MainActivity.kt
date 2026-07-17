package com.clinic.neochild.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
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
import com.clinic.neochild.app.AppNavigation
import com.clinic.neochild.app.Routes
import com.clinic.neochild.core.designsystem.NeoChildTheme
import com.clinic.neochild.notification.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var auth: FirebaseAuth
    
    @Inject
    lateinit var firestore: FirebaseFirestore
    
    @Inject
    lateinit var messaging: FirebaseMessaging

    @Inject
    lateinit var notificationHelper: NotificationHelper

    private var lastActiveTime: Long = System.currentTimeMillis()
    private val SESSION_TIMEOUT = 15 * 60 * 1000 // 15 minutes auto-lock
    
    private var openDueTab by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        
        notificationHelper.cancelSummaryNotification()

        // SECURITY: Prevent screenshots and recording of patient data
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        
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

    override fun onResume() {
        super.onResume()
        checkSession()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        lastActiveTime = System.currentTimeMillis()
    }

    private fun checkSession() {
        val currentTime = System.currentTimeMillis()
        if (auth.currentUser != null && (currentTime - lastActiveTime > SESSION_TIMEOUT)) {
            auth.signOut()
        }
        lastActiveTime = currentTime
    }

    private fun fetchAndStoreFcmToken() {
        val currentUser = auth.currentUser ?: return
        messaging.token.addOnSuccessListener { token ->
            firestore.collection("users").document(currentUser.uid)
                .update("fcmToken", token)
                .addOnFailureListener {
                    val data = hashMapOf("fcmToken" to token, "email" to currentUser.email)
                    firestore.collection("users").document(currentUser.uid).set(data)
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
