package com.aiquota.app.domain.repository

/**
 * 额度通知去重：同一 bucket 在同一阈值下只提醒一次，
 * 额度恢复后清除，允许重新进入提醒周期。
 */
interface NotificationRepository {
    suspend fun isNotified(bucketId: String, threshold: Int): Boolean
    suspend fun markNotified(bucketId: String, threshold: Int)
    /** 额度恢复后清除该 bucket 全部提醒标记 */
    suspend fun clearRecovery(bucketId: String)
    /** 仅当阈值已被触发过且当前已恢复（remainingPercent > threshold）才清除 */
    suspend fun evaluate(bucketId: String, threshold: Int, currentPercent: Double?)
}