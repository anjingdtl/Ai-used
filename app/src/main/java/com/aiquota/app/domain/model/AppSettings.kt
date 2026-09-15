package com.aiquota.app.domain.model

/**
 * 刷新间隔策略。
 *
 * [durationMillis] 为真实毫秒数：30s/1m/2m/5m/手动(0)。
 * MANUAL 停止前台定时刷新；>0 的值供前台 Dashboard 可见时按此间隔自动刷新。
 * （后台周期刷新由 WorkManager 单独负责，见 QuotaSyncScheduler。）
 */
enum class RefreshInterval(val labelId: String, val durationMillis: Long) {
    S30S("30 秒", 30_000L),
    MIN1("1 分钟", 60_000L),
    MIN2("2 分钟", 120_000L),
    MIN5("5 分钟", 300_000L),
    MANUAL("仅手动", 0L);
}

/** 全局应用设置 */
data class AppSettings(
    val autoRefreshInterval: RefreshInterval = RefreshInterval.MIN2,
    val realtimeMonitoringEnabled: Boolean = false,
    val notificationEnabled: Boolean = true,
    /** 通知阈值百分比（30/20/10/5/0） */
    val thresholds: List<Int> = listOf(30, 20, 10, 5, 0),
    val dataRetentionDays: Int = 90,
    val onboardingCompleted: Boolean = false
)