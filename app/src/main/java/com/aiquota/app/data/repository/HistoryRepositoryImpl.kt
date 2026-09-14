package com.aiquota.app.data.repository

import com.aiquota.app.data.database.dao.HistoryDao
import com.aiquota.app.data.database.entity.QuotaHistoryEntity
import com.aiquota.app.data.mapper.Mapper
import com.aiquota.app.domain.model.QuotaSnapshot
import com.aiquota.app.domain.repository.HistoryRepository
import com.aiquota.app.domain.repository.QuotaHistoryPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

/** 历史额度点记录。 */
class HistoryRepositoryImpl @Inject constructor(
    private val historyDao: HistoryDao
) : HistoryRepository {

    override fun observePoints(
        accountId: String,
        bucketId: String,
        since: Instant,
        until: Instant
    ): Flow<List<QuotaHistoryPoint>> =
        historyDao.observePoints(
            accountId,
            bucketId,
            since.toEpochMilli(),
            until.toEpochMilli()
        ).map { list -> list.map { Mapper.toHistory(it) } }

    override suspend fun record(snapshot: QuotaSnapshot) {
        val at = snapshot.cachedAt.toEpochMilli()
        snapshot.buckets.filter { it.isKeyBucket }.forEach { bucket ->
            // 若同一分钟同桶已有点，则跳过，避免过度采样
            val latest = historyDao.latest(snapshot.accountId, bucket.id)
            if (latest != null &&
                at - latest.recordedAt < 60_000 &&
                latest.remainingPercent == bucket.remainingPercent
            ) return@forEach
            historyDao.insert(
                QuotaHistoryEntity(
                    accountId = snapshot.accountId,
                    bucketId = bucket.id,
                    remainingPercent = bucket.remainingPercent,
                    remaining = bucket.remaining,
                    recordedAt = at
                )
            )
        }
    }

    override suspend fun prune(keepSince: Instant) {
        historyDao.pruneBefore(keepSince.toEpochMilli())
    }
}