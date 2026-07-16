package com.clinic.neochild.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.clinic.neochild.MainActivity
import com.clinic.neochild.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_VACCINATION = "vaccination_reminders"
        const val CHANNEL_INVENTORY = "inventory_alerts"
        const val CHANNEL_EXPIRY = "expiry_alerts"
        
        const val SUMMARY_ID = 1000
        const val GROUP_VACCINATION = "com.clinic.neochild.VACCINATION"
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vaccinationChannel = NotificationChannel(
                CHANNEL_VACCINATION,
                "Vaccination Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders for due and overdue vaccinations"
            }

            val inventoryChannel = NotificationChannel(
                CHANNEL_INVENTORY,
                "Inventory Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for low or out of stock vaccines"
            }

            val expiryChannel = NotificationChannel(
                CHANNEL_EXPIRY,
                "Expiry Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts for upcoming vaccine expiry"
            }

            notificationManager.createNotificationChannels(
                listOf(vaccinationChannel, inventoryChannel, expiryChannel)
            )
        }
    }

    fun showVaccinationNotification(
        id: Int,
        title: String,
        content: String,
        patientId: String? = null,
        vaccinationId: String? = null,
        reminderId: Long = -1L
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("patientId", patientId)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 
            id, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Dismiss Action
        val dismissIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = "ACTION_DISMISS"
            putExtra("reminderId", reminderId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context, id + 2, dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_VACCINATION)
            .setSmallIcon(R.drawable.app_logo)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setGroup(GROUP_VACCINATION)
            .addAction(0, "Dismiss", dismissPendingIntent)

        NotificationManagerCompat.from(context).notify(id, builder.build())
        showSummaryNotification()
    }

    private fun showSummaryNotification() {
        val summaryNotification = NotificationCompat.Builder(context, CHANNEL_VACCINATION)
            .setSmallIcon(R.drawable.app_logo)
            .setStyle(NotificationCompat.InboxStyle()
                .setSummaryText("Vaccination Reminders"))
            .setGroup(GROUP_VACCINATION)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(SUMMARY_ID, summaryNotification)
    }

    fun showInventoryAlert(id: Int, title: String, content: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_INVENTORY)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    fun showExpiryAlert(id: Int, title: String, content: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_EXPIRY)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    fun cancelNotification(id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }

    fun cancelAllVaccinationNotifications() {
        notificationManager.cancelAll()
    }
}
