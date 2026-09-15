package com.aiquota.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aiquota.app.core.notify.QuotaNotifier
import com.aiquota.app.domain.repository.HistoryRepository
import com.aiquota.app.domain.repository.NotificationRepository
import com.aiquota.app.domain.repository.QuotaRepository
import com.aiquota.app.domain.repository.SettingsRepository
import com.aiquota.app.widget.QuotaWidgetProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 后台周期同步：刷新所有启用账户额度，并在刷新后评估通知阈值。
 * 同时每轮执行历史数据清理（History prune，按 dataRetentionDays）。
 */
@HiltWorker
class QuotaSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val quotaRepository: QuotaRepository,
    private val settingsRepository: SettingsRepository,
    private val notificationRepository: NotificationRepository,
    private val historyRepository: HistoryRepository,
    private val notifier: QuotaNotifier
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            quotaRepository.refreshAll()
            pruneHistory()
            QuotaWidgetProvider.requestUpdate(applicationContext)
            evaluateIfAny()
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }

    /** P1-3：真正执行历史清理——按 dataRetentionDays 删除过期点。 */
    private suspend fun pruneHistory() {
        val settings = settingsRepository.getSettings()
        val keepSince = Instant.now().minus(settings.dataRetentionDays.toLong(), ChronoUnit.DAYS)
        historyRepository.prune(keepSince)
    }

    private suspend fun evaluateIfAny() {
        val settings = settingsRepository.getSettings()
        if (!settings.notificationEnabled) return
        val thresholds = settings.thresholds.sortedDescending()
        if (thresholds.isEmpty()) return

        val states = quotaRepository.observeEnabledStates().first()
        for (state in states) {
            val snapshot = state.snapshot ?: continue
            for (bucket in snapshot.buckets.filter { it.isKeyBucket }) {
                val percent = bucket.remainingPercent ?: continue
                val dedupKey = "${state.accountId}:${bucket.id}"
                // P1-2：额度恢复到“所有已触发阈值 + recoveryMargin”之上时清除旧标记，
                // 允许以后再次跌破时重新提醒（如 30% 通知 -> 80% 恢复清除 -> 29% 再次通知）。
                for (threshold in thresholds) {
                    if (percent > threshold + RECOVERY_MARGIN) {
                        notificationRepository.evaluate(dedupKey, threshold, percent)
                    }
                }
                val hit = thresholds.firstOrNull { percent <= it } ?: continue
                if (!notificationRepository.isNotified(dedupKey, hit)) {
                    notificationRepository.markNotified(dedupKey, hit)
                    notifier.notifyThreshold(snapshot.accountName, bucket.name, percent)
                }
            }
        }
    }

    private companion object {
        /** 额度恢复判定余量：remainingPercent 超过 阈值 + margin 才视为“已恢复”。 */
        const val RECOVERY_MARGIN = 5.0
    }
}