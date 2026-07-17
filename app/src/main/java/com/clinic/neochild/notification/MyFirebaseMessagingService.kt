package com.clinic.neochild.notification

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MyFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var notificationHelper: NotificationHelper

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        // Handle cloud messages if needed
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
    }
}
