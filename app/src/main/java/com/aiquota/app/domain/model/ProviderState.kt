package com.aiquota.app.domain.model

import java.time.Instant

/** 单个 Provider 账户的 UI 状态 */
data class ProviderState(
    val accountId: String,
    val snapshot: QuotaSnapshot? = null,
    val syncStatus: SyncStatus = SyncStatus.FAILED_NO_CACHE,
    val lastAttemptAt: Instant? = null,
    val lastSuccessAt: Instant? = null,
    val error: QueryError? = null
) {
    val hasCachedData: Boolean get() = snapshot != null
}

/** 顶部汇总状态 */
data class DashboardOverview(
    val minRemainingPercent: Double?,
    val totalEnabled: Int,
    val liveCount: Int,
    val cachedCount: Int,
    val syncingCount: Int,
    val unavailableCount: Int,
    val updatedAt: Instant?
)