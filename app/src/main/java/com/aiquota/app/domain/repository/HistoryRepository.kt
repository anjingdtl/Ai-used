package com.aiquota.app.domain.repository

import com.aiquota.app.domain.model.QuotaBucket
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** 每一次额度变化的历史点 */
data class QuotaHistoryPoint(
    val id: Long,
    val accountId: String,
    val bucketId: String,
    val remainingPercent: Double?,
    val remaining: Double?,
    val recordedAt: Instant
)

interface HistoryRepository {
    fun observePoints(accountId: String, bucketId: String, since: Instant, until: Instant): Flow<List<QuotaHistoryPoint>>
    /** 记录一个点（来自成功快照） */
    suspend fun record(snapshot: com.aiquota.app.domain.model.QuotaSnapshot)
    /** 清理过期历史（默认保留 90 天） */
    suspend fun prune(keepSince: Instant)
}

/** 同步记录 */
data class SyncEvent(
    val accountId: String,
    val status: com.aiquota.app.domain.model.SyncStatus,
    val errorMessage: String? = null,
    val at: Instant = Instant.now()
)