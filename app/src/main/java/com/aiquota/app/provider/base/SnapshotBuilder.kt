package com.aiquota.app.provider.base

import com.aiquota.app.domain.model.QuotaSnapshot
import com.aiquota.app.domain.model.QuotaBucket
import com.aiquota.app.domain.model.DataSource
import java.time.Instant
import java.util.UUID

/** 构建 QuotaSnapshot 的辅助工厂，所有 Provider 复用 */
object SnapshotBuilder {
    fun build(
        providerId: String,
        accountId: String,
        accountName: String,
        planName: String?,
        buckets: List<QuotaBucket>,
        balance: com.aiquota.app.domain.model.Balance? = null,
        source: DataSource = DataSource.NETWORK,
        queriedAt: Instant = Instant.now()
    ): QuotaSnapshot {
        val now = Instant.now()
        return QuotaSnapshot(
            id = accountId,
            providerId = providerId,
            accountId = accountId,
            accountName = accountName,
            planName = planName ?: "未知套餐",
            buckets = buckets,
            balance = balance,
            queriedAt = queriedAt.let { if (it.isAfter(now)) now else it },
            cachedAt = now,
            source = source
        )
    }
}