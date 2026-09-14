package com.aiquota.app.domain.repository

import com.aiquota.app.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observeSettings(): Flow<AppSettings>
    suspend fun getSettings(): AppSettings
    suspend fun update(transform: (AppSettings) -> AppSettings)
    suspend fun setThresholds(thresholds: List<Int>)
    suspend fun setRealtimeMonitoring(enabled: Boolean)
    suspend fun setRefreshInterval(minutes: Int)
    suspend fun markOnboardingCompleted()
}