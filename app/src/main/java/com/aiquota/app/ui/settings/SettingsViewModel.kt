package com.aiquota.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiquota.app.domain.model.AppSettings
import com.aiquota.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> =
        settingsRepository.observeSettings()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setRealtimeMonitoring(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setRealtimeMonitoring(enabled) }
    }

    fun setRefreshIntervalMillis(millis: Long) {
        viewModelScope.launch { settingsRepository.setRefreshInterval(millis) }
    }

    fun setThresholds(thresholds: List<Int>) {
        viewModelScope.launch { settingsRepository.setThresholds(thresholds) }
    }
}