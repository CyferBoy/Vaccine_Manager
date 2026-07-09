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
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val db: AppDatabase,
    private val firestore: FirebaseFirestore
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // 1. Sync Local Data to Firestore using Batches for efficiency
        syncData()

        // 2. Check for Today's Due Vaccinations locally for offline support
        checkAndNotifyDueVaccines()

        return Result.success()
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
            runCatching {
                batch.commit().await()
                unsyncedPatients.forEach { db.patientDao().markSynced(it.id) }
            }
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
                runCatching {
                    batch.commit().await()
                    chunk.forEach { db.vaccinationDao().markSynced(it.id) }
                }
            }
        }
    }

    private suspend fun checkAndNotifyDueVaccines() {
        val sharedPrefs = applicationContext.getSharedPreferences("neochild_prefs", Context.MODE_PRIVATE)
        val todayStr = SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(Date())
        val lastNotifyDate = sharedPrefs.getString("last_due_notify_date", "")

        if (lastNotifyDate == todayStr) return

        // Query Room instead of Firestore: Offline support + 0 Cloud Read cost
        val dueCount = db.vaccinationDao().getDueCount(todayStr)
        
        if (dueCount > 0) {
            showDueNotification(dueCount)
            sharedPrefs.edit().putString("last_due_notify_date", todayStr).apply()
        }
    }

    private fun showDueNotification(count: Int) {
        val channelId = "neochild_notifications"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_DUE_TAB", true)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 101, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.app_logo)
            .setContentTitle("Due Today")
            .setContentText("You have $count vaccination(s) scheduled for today.")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        notificationManager.notify(202, notification)
    }
}
