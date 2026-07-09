package com.clinic.neochild

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.*
import com.clinic.neochild.notification.ReminderScheduler
import com.clinic.neochild.sync.SyncWorker
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.initialize
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class NeoChildApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        
        setupFirebaseAppCheck()

        // Manually load SQLCipher native library (Required for version 4.6.1+ and 16KB support)
        System.loadLibrary("sqlcipher")
        setupSync()
        reminderScheduler.schedulePeriodicReminders()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "neochild_notifications",
                "Clinic Notifications",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "General notifications for vaccine updates and clinic info"
            }
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun setupFirebaseAppCheck() {
        Firebase.initialize(context = this)
        
        // For testing purposes, we use DebugAppCheckProviderFactory even in release.
        // This allows using a debug secret token on any device.
        Log.d(TAG, "Using DebugAppCheckProviderFactory for testing")
        val factory = DebugAppCheckProviderFactory.getInstance()
        
        Firebase.appCheck.installAppCheckProviderFactory(factory)
        Log.d(TAG, "App Check initialized")
    }

    private fun setupSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest,
        )
    }

    companion object {
        private const val TAG = "AppCheck"
        private const val SYNC_WORK_NAME = "SyncWork"
    }
}
