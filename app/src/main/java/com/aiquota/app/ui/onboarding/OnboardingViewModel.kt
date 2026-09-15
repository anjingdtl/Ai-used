package com.aiquota.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiquota.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 引导页状态与动作：负责读取/标记「引导已完成」。 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    /** true=已完成引导。 */
    val onboardingCompleted: Flow<Boolean> = settingsRepository.observeSettings()
        .map { it.onboardingCompleted }

    /** 用户点击「开始使用」，持久化标记，后续冷启动直达主页。 */
    fun markCompleted() {
        viewModelScope.launch { markCompletedSuspended() }
    }

    /** 完成标记并**等待** DataStore 落盘后再返回（P1-5）。避免 app 在写入完成前被杀导致下次仍进引导页。 */
    suspend fun markCompletedSuspended() = settingsRepository.markOnboardingCompleted()
}