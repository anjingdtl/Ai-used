package com.aiquota.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aiquota.app.domain.model.AppSettings
import com.aiquota.app.domain.model.RefreshInterval
import com.aiquota.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

class DataStoreSettingsRepository(private val context: Context) : SettingsRepository {

    private object Keys {
        val REFRESH_MINUTES = intPreferencesKey("refresh_minutes")
        val REALTIME = booleanPreferencesKey("realtime_monitoring")
        val NOTIF = booleanPreferencesKey("notification_enabled")
        val THRESHOLDS = stringSetPreferencesKey("thresholds")
        val RETENTION_DAYS = intPreferencesKey("retention_days")
        val ONBOARDED = booleanPreferencesKey("onboarding_completed")
    }

    override fun observeSettings(): Flow<AppSettings> =
        context.dataStore.data.map { p ->
            AppSettings(
                autoRefreshInterval = intervalFromMinutes(p[Keys.REFRESH_MINUTES] ?: RefreshInterval.MIN2.minutes),
                realtimeMonitoringEnabled = p[Keys.REALTIME] ?: false,
                notificationEnabled = p[Keys.NOTIF] ?: true,
                thresholds = (p[Keys.THRESHOLDS] ?: setOf("30", "20", "10", "5", "0"))
                    .mapNotNull { it.toIntOrNull() }.sortedDescending(),
                dataRetentionDays = p[Keys.RETENTION_DAYS] ?: 90,
                onboardingCompleted = p[Keys.ONBOARDED] ?: false
            )
        }

    override suspend fun getSettings(): AppSettings {
        val p = context.dataStore.data.first()
        return AppSettings(
            autoRefreshInterval = intervalFromMinutes(p[Keys.REFRESH_MINUTES] ?: RefreshInterval.MIN2.minutes),
            realtimeMonitoringEnabled = p[Keys.REALTIME] ?: false,
            notificationEnabled = p[Keys.NOTIF] ?: true,
            thresholds = (p[Keys.THRESHOLDS] ?: setOf("30", "20", "10", "5", "0"))
                .mapNotNull { it.toIntOrNull() }.sortedDescending(),
            dataRetentionDays = p[Keys.RETENTION_DAYS] ?: 90,
            onboardingCompleted = p[Keys.ONBOARDED] ?: false
        )
    }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        val current = getSettings()
        val next = transform(current)
        context.dataStore.edit { p ->
            p[Keys.REFRESH_MINUTES] = next.autoRefreshInterval.minutes
            p[Keys.REALTIME] = next.realtimeMonitoringEnabled
            p[Keys.NOTIF] = next.notificationEnabled
            p[Keys.THRESHOLDS] = next.thresholds.map { it.toString() }.toSet()
            p[Keys.RETENTION_DAYS] = next.dataRetentionDays
            p[Keys.ONBOARDED] = next.onboardingCompleted
        }
    }

    override suspend fun setThresholds(thresholds: List<Int>) = update { it.copy(thresholds = thresholds) }

    override suspend fun setRealtimeMonitoring(enabled: Boolean) =
        update { it.copy(realtimeMonitoringEnabled = enabled) }

    override suspend fun setRefreshInterval(minutes: Int) =
        update { it.copy(autoRefreshInterval = intervalFromMinutes(minutes)) }

    override suspend fun markOnboardingCompleted() =
        update { it.copy(onboardingCompleted = true) }

    private fun intervalFromMinutes(minutes: Int): RefreshInterval =
        RefreshInterval.entries.firstOrNull { it.minutes == minutes } ?: RefreshInterval.MIN2
}