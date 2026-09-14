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
        val recovered = currentPercent == null || currentPercent > threshold
        if (isNotified(bucketId, threshold) && recovered) {
            clearRecovery(bucketId)
        }
    }
}