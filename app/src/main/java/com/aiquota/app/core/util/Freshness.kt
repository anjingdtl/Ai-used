package com.aiquota.app.core.util

import com.aiquota.app.domain.model.SyncStatus
import com.aiquota.app.domain.model.QuotaSnapshot
import java.time.Duration
import java.time.Instant

/**
 * 数据新鲜度。仅改变展示状态，绝不删除额度数字。
 */
fun freshness(queriedAt: Instant, now: Instant = Instant.now()): Freshness {
    val age = Duration.between(queriedAt, now)
    return when {
        age < Duration.ofMinutes(5) -> Freshness.REALTIME
        age < Duration.ofMinutes(30) -> Freshness.RECENT
        age < Duration.ofHours(6) -> Freshness.CACHED
        else -> Freshness.STALE
    }
}

enum class Freshness {
    /** 0～5 分钟 */
    REALTIME,
    /** 5～30 分钟 */
    RECENT,
    /** 30 分钟～6 小时 */
    CACHED,
    /** 超过 6 小时 */
    STALE;

    fun label(): String = when (this) {
        REALTIME -> "实时"
        RECENT -> "较新"
        CACHED -> "缓存数据"
        STALE -> "数据可能已过期"
    }
}

/** 将快照来源 + 新鲜度归一到 SyncStatus 展示语义 */
fun displayStatus(
    hasData: Boolean,
    inFlight: Boolean,
    lastError: Boolean
): SyncStatus = when {
    inFlight -> SyncStatus.SYNCING
    !hasData && lastError -> SyncStatus.FAILED_NO_CACHE
    !hasData -> SyncStatus.FAILED_NO_CACHE
    lastError -> SyncStatus.CACHED
    else -> SyncStatus.LIVE
}