package com.aiquota.app.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aiquota.app.data.database.entity.QuotaHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(point: QuotaHistoryEntity)

    @Query("SELECT * FROM quota_history WHERE accountId = :accountId AND bucketId = :bucketId " +
            "AND recordedAt BETWEEN :since AND :until ORDER BY recordedAt ASC")
    fun observePoints(accountId: String, bucketId: String, since: Long, until: Long): Flow<List<QuotaHistoryEntity>>

    @Query("DELETE FROM quota_history WHERE recordedAt < :cutoff")
    suspend fun pruneBefore(cutoff: Long)

    @Query("SELECT * FROM quota_history WHERE accountId = :accountId AND bucketId = :bucketId " +
            "ORDER BY recordedAt DESC LIMIT 1")
    suspend fun latest(accountId: String, bucketId: String): QuotaHistoryEntity?

    @Query("SELECT COUNT(*) FROM quota_history")
    suspend fun count(): Int
}