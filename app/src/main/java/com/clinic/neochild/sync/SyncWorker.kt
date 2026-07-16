package com.clinic.neochild.sync

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.clinic.neochild.MainActivity
import com.clinic.neochild.R
import com.clinic.neochild.data.local.AppDatabase
import com.clinic.neochild.data.local.entity.toPatient
import com.clinic.neochild.data.local.entity.toVaccination
import com.clinic.neochild.utils.Constants
import com.google.firebase.firestore.FirebaseFirestore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val db: AppDatabase,
    private val firestore: FirebaseFirestore,
    private val settingsManager: com.clinic.neochild.data.local.preferences.NotificationSettingsManager,
    private val notificationHelper: com.clinic.neochild.notification.NotificationHelper
) : CoroutineWorker(appContext, workerParams) {

    private val sharedPrefs = appContext.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)

    override suspend fun doWork(): Result {
        return try {
            syncData()
            sharedPrefs.edit().putInt("failure_count", 0).apply()
            Result.success()
        } catch (e: Exception) {
            val count = sharedPrefs.getInt("failure_count", 0) + 1
            sharedPrefs.edit().putInt("failure_count", count).apply()
            
            val settings = settingsManager.settingsFlow.first()
            if (count >= 5 && settings.syncAlertsEnabled) {
                notificationHelper.showSyncAlert(e.message ?: "Unknown error")
            }
            Result.retry()
        }
    }

    private suspend fun syncData() {
        val unsyncedPatients = db.patientDao().getUnsyncedPatients()
        if (unsyncedPatients.isNotEmpty()) {
            val batch = firestore.batch()
            unsyncedPatients.forEach { entity ->
                val docRef = firestore.collection("patients").document(entity.id)
                if (entity.isDeleted) batch.delete(docRef)
                else batch.set(docRef, entity.toPatient())
            }
            batch.commit().await()
            unsyncedPatients.forEach { db.patientDao().markSynced(it.id) }
        }

        val unsyncedVaccinations = db.vaccinationDao().getUnsyncedVaccinations()
        if (unsyncedVaccinations.isNotEmpty()) {
            // Firestore batches are limited to 500 operations
            unsyncedVaccinations.chunked(500).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { entity ->
                    val docRef = firestore.collection("vaccinations").document(entity.id)
                    if (entity.isDeleted) batch.delete(docRef)
                    else batch.set(docRef, entity.toVaccination())
                }
                batch.commit().await()
                chunk.forEach { db.vaccinationDao().markSynced(it.id) }
            }
        }
    }
}
