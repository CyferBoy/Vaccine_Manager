package com.clinic.neochild.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.clinic.neochild.MainActivity
import com.clinic.neochild.R
import com.clinic.neochild.data.local.AppDatabase
import com.clinic.neochild.data.local.entity.toPatient
import com.clinic.neochild.data.local.entity.toVaccination
import com.clinic.neochild.utils.Constants
import com.clinic.neochild.utils.FirestoreMappers
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getDatabase(applicationContext)
        val firestore = FirebaseFirestore.getInstance()

        // 1. Sync Local Data to Firestore
        syncData(db, firestore)

        // 2. Check for Today's Due Vaccinations and Notify
        checkAndNotifyDueVaccines(firestore)

        return Result.success()
    }

    private suspend fun syncData(db: AppDatabase, firestore: FirebaseFirestore) {
        // Sync Patients
        val unsyncedPatients = db.patientDao().getUnsyncedPatients()
        for (entity in unsyncedPatients) {
            try {
                if (entity.isDeleted) {
                    firestore.collection("patients").document(entity.id).delete().await()
                } else {
                    firestore.collection("patients").document(entity.id).set(entity.toPatient()).await()
                }
                db.patientDao().markSynced(entity.id)
            } catch (_: Exception) { }
        }

        // Sync Vaccinations
        val unsyncedVaccinations = db.vaccinationDao().getUnsyncedVaccinations()
        for (entity in unsyncedVaccinations) {
            try {
                val vaccination = entity.toVaccination()
                if (entity.isDeleted) {
                    firestore.collection("vaccinations").document(entity.id).delete().await()
                } else {
                    firestore.collection("vaccinations").document(entity.id).set(vaccination).await()
                }
                db.vaccinationDao().markSynced(entity.id)
            } catch (_: Exception) { }
        }
    }

    private suspend fun checkAndNotifyDueVaccines(firestore: FirebaseFirestore) {
        val sharedPrefs = applicationContext.getSharedPreferences("neochild_prefs", Context.MODE_PRIVATE)
        val todayStr = SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(Date())
        val lastNotifyDate = sharedPrefs.getString("last_due_notify_date", "")

        // Only notify once per day
        if (lastNotifyDate == todayStr) return

        try {
            val snapshot = firestore.collection("vaccinations")
                .whereEqualTo("nextDueDate", todayStr)
                .whereEqualTo("isDone", false)
                .get()
                .await()

            val dueCount = snapshot.size()
            if (dueCount > 0) {
                showDueNotification(dueCount)
                sharedPrefs.edit().putString("last_due_notify_date", todayStr).apply()
            }
        } catch (e: Exception) {
            // Handle or log error
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
