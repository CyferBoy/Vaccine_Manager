package com.clinic.neochild.features.settings

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
    private val DAILY_SUMMARY_ENABLED = booleanPreferencesKey("daily_summary_enabled")
    private val LOW_STOCK_ENABLED = booleanPreferencesKey("low_stock_enabled")
    private val SYNC_ALERTS_ENABLED = booleanPreferencesKey("sync_alerts_enabled")
    private val REMINDER_TIME = stringPreferencesKey("reminder_time")
    private val LOW_STOCK_THRESHOLD = intPreferencesKey("low_stock_threshold")
    
    // Internal tracking
    private val LAST_SUMMARY_SENT_DATE = stringPreferencesKey("last_summary_sent_date")
    private val NOTIFIED_LOW_STOCK_VACCINES = stringSetPreferencesKey("notified_low_stock_vaccines")

    val settingsFlow: Flow<NotificationSettings> = context.dataStore.data.map { preferences ->
        NotificationSettings(
            dailySummaryEnabled = preferences[DAILY_SUMMARY_ENABLED] ?: true,
            lowStockEnabled = preferences[LOW_STOCK_ENABLED] ?: true,
            syncAlertsEnabled = preferences[SYNC_ALERTS_ENABLED] ?: true,
            reminderTime = preferences[REMINDER_TIME] ?: "08:00",
            lowStockThreshold = preferences[LOW_STOCK_THRESHOLD] ?: 5,
            lastSummarySentDate = preferences[LAST_SUMMARY_SENT_DATE] ?: "",
            notifiedLowStockVaccines = preferences[NOTIFIED_LOW_STOCK_VACCINES] ?: emptySet()
        )
    }

    suspend fun updateSettings(settings: NotificationSettings) {
        context.dataStore.edit { preferences ->
            preferences[DAILY_SUMMARY_ENABLED] = settings.dailySummaryEnabled
            preferences[LOW_STOCK_ENABLED] = settings.lowStockEnabled
            preferences[SYNC_ALERTS_ENABLED] = settings.syncAlertsEnabled
            preferences[REMINDER_TIME] = settings.reminderTime
            preferences[LOW_STOCK_THRESHOLD] = settings.lowStockThreshold
        }
    }

    suspend fun markSummarySent(dateStr: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_SUMMARY_SENT_DATE] = dateStr
        }
    }

    suspend fun markLowStockNotified(vaccineId: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[NOTIFIED_LOW_STOCK_VACCINES] ?: emptySet()
            preferences[NOTIFIED_LOW_STOCK_VACCINES] = current + vaccineId
        }
    }

    suspend fun clearLowStockNotification(vaccineId: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[NOTIFIED_LOW_STOCK_VACCINES] ?: emptySet()
            preferences[NOTIFIED_LOW_STOCK_VACCINES] = current - vaccineId
        }
    }
}

data class NotificationSettings(
    val dailySummaryEnabled: Boolean,
    val lowStockEnabled: Boolean,
    val syncAlertsEnabled: Boolean,
    val reminderTime: String,
    val lowStockThreshold: Int,
    val lastSummarySentDate: String,
    val notifiedLowStockVaccines: Set<String>
)
