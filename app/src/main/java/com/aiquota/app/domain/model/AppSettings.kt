package com.aiquota.app.domain.model

/** 刷新间隔策略 */
enum class RefreshInterval(val labelId: String, val minutes: Int) {
    S30S("30 秒", 1),   // 前台 30 秒用 1 分钟占位（后台仅用于实时监控）
    MIN1("1 分钟", 1),
    MIN2("2 分钟", 2),
    MIN5("5 分钟", 5),
    MANUAL("仅手动", 0);
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