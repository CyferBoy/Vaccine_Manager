package com.clinic.neochild.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "notification_settings")

@Singleton
class NotificationSettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    private val REMINDER_TIME = stringPreferencesKey("reminder_time")
    private val REMINDER_DAYS_BEFORE = intPreferencesKey("reminder_days_before")
    private val OVERDUE_FREQUENCY = intPreferencesKey("overdue_frequency")
    private val LOW_STOCK_THRESHOLD = intPreferencesKey("low_stock_threshold")
    private val EXPIRY_DAYS_BEFORE = intPreferencesKey("expiry_days_before")
    private val SMS_ENABLED = booleanPreferencesKey("sms_enabled")

    val settingsFlow: Flow<NotificationSettings> = context.dataStore.data.map { preferences ->
        NotificationSettings(
            enabled = preferences[NOTIFICATIONS_ENABLED] ?: true,
            reminderTime = preferences[REMINDER_TIME] ?: "08:00",
            reminderDaysBefore = preferences[REMINDER_DAYS_BEFORE] ?: 1,
            overdueFrequencyDays = preferences[OVERDUE_FREQUENCY] ?: 2,
            lowStockThreshold = preferences[LOW_STOCK_THRESHOLD] ?: 5,
            expiryDaysBefore = preferences[EXPIRY_DAYS_BEFORE] ?: 30,
            smsEnabled = preferences[SMS_ENABLED] ?: false
        )
    }

    suspend fun updateSettings(settings: NotificationSettings) {
        context.dataStore.edit { preferences ->
            preferences[NOTIFICATIONS_ENABLED] = settings.enabled
            preferences[REMINDER_TIME] = settings.reminderTime
            preferences[REMINDER_DAYS_BEFORE] = settings.reminderDaysBefore
            preferences[OVERDUE_FREQUENCY] = settings.overdueFrequencyDays
            preferences[LOW_STOCK_THRESHOLD] = settings.lowStockThreshold
            preferences[EXPIRY_DAYS_BEFORE] = settings.expiryDaysBefore
            preferences[SMS_ENABLED] = settings.smsEnabled
        }
    }
}

data class NotificationSettings(
    val enabled: Boolean,
    val reminderTime: String,
    val reminderDaysBefore: Int,
    val overdueFrequencyDays: Int,
    val lowStockThreshold: Int,
    val expiryDaysBefore: Int,
    val smsEnabled: Boolean
)
