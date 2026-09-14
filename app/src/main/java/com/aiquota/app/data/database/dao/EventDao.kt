package com.aiquota.app.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aiquota.app.data.database.entity.NotificationEventEntity
import com.aiquota.app.data.database.entity.SyncEventEntity

@Dao
interface EventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncEvent(e: SyncEventEntity)

    @Query("SELECT * FROM sync_event WHERE accountId = :accountId ORDER BY at DESC LIMIT :limit")
    suspend fun recentSyncEvents(accountId: String, limit: Int = 50): List<SyncEventEntity>

    // ---- 通知去重 ----
    @Query("SELECT COUNT(*) FROM notification_event WHERE bucketId = :bucketId AND threshold = :threshold")
    suspend fun isNotified(bucketId: String, threshold: Int): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(e: NotificationEventEntity)

    @Query("DELETE FROM notification_event WHERE bucketId = :bucketId")
    suspend fun clearNotificationsForBucket(bucketId: String)
}