package com.aiquota.app.data.repository

import com.aiquota.app.data.database.dao.EventDao
import com.aiquota.app.domain.repository.NotificationRepository

/** 额度通知去重：同一 bucket 在同一阈值下只提醒一次，恢复后清除。 */
class NotificationRepositoryImpl(
    private val eventDao: EventDao
) : NotificationRepository {

    override suspend fun isNotified(bucketId: String, threshold: Int): Boolean =
        eventDao.isNotified(bucketId, threshold) > 0

    override suspend fun markNotified(bucketId: String, threshold: Int) {
        eventDao.insertNotification(
            com.aiquota.app.data.database.entity.NotificationEventEntity(
                bucketId = bucketId,
                threshold = threshold,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun clearRecovery(bucketId: String) {
        eventDao.clearNotificationsForBucket(bucketId)
    }

    override suspend fun evaluate(bucketId: String, threshold: Int, currentPercent: Double?) {
        // 保守策略：仅当前值非空且严格高于阈值才视为“恢复并清除”。
        // null（未知额度）绝不当作恢复，避免在数据缺失时误清通知标记。
        val recovered = currentPercent != null && currentPercent > threshold
        if (isNotified(bucketId, threshold) && recovered) {
            clearRecovery(bucketId)
        }
    }
}