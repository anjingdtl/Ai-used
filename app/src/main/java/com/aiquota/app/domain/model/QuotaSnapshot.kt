package com.aiquota.app.domain.model

import java.time.Instant

/**
 * 最后有效额度快照（Last Known Good Snapshot）。
 * 每次成功查询后持久化，查询失败不得覆盖。
 */
data class QuotaSnapshot(
    val id: String,
    val providerId: String,
    val accountId: String,
    val accountName: String,
    val planName: String? = null,
    val buckets: List<QuotaBucket> = emptyList(),
    val balance: Balance? = null,
    val queriedAt: Instant,
    val cachedAt: Instant,
    val source: DataSource
) {
    /** 关键桶中最低的 remainingPercent */
    val minRemainingPercent: Double?
        get() = buckets
            .filter { it.isKeyBucket && it.remainingPercent != null }
            .mapNotNull { it.remainingPercent }
            .minOrNull()

    /** 账户级连通性：是否有任何关键桶 */
    val isValid: Boolean
        get() = buckets.any { it.isKeyBucket } || balance != null
}