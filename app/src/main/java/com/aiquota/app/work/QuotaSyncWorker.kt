package com.aiquota.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aiquota.app.core.notify.QuotaNotifier
import com.aiquota.app.domain.repository.NotificationRepository
import com.aiquota.app.domain.repository.QuotaRepository
import com.aiquota.app.domain.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * 后台周期同步：刷新所有启用账户额度，并在刷新后评估通知阈值。
 */
@HiltWorker
class QuotaSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val quotaRepository: QuotaRepository,
    private val settingsRepository: SettingsRepository,
    private val notificationRepository: NotificationRepository,
    private val notifier: QuotaNotifier
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            quotaRepository.refreshAll()
            evaluateIfAny()
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }

    private suspend fun evaluateIfAny() {
        if (!settingsRepository.getSettings().notificationEnabled) return
        val thresholds = settingsRepository.getSettings().thresholds.sortedDescending()
        if (thresholds.isEmpty()) return

        val states = quotaRepository.observeEnabledStates().first()
        for (state in states) {
            val snapshot = state.snapshot ?: continue
            for (bucket in snapshot.buckets.filter { it.isKeyBucket }) {
                val percent = bucket.remainingPercent ?: continue
                val hit = thresholds.firstOrNull { percent <= it } ?: continue
                val dedupKey = "${state.accountId}:${bucket.id}"
                if (!notificationRepository.isNotified(dedupKey, hit)) {
                    notificationRepository.markNotified(dedupKey, hit)
                    notifier.notifyThreshold(snapshot.accountName, bucket.name, percent)
                }
            }
        }
    }
}