package com.aiquota.app.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aiquota.app.data.database.entity.CredentialEntity
import com.aiquota.app.data.database.entity.LatestQuotaSnapshotEntity
import com.aiquota.app.data.database.entity.QuotaBucketEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuotaDao {

    // ---- 凭据 ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCredential(c: CredentialEntity)

    @Query("SELECT * FROM credential WHERE accountId = :accountId")
    suspend fun getCredential(accountId: String): CredentialEntity?

    @Query("DELETE FROM credential WHERE accountId = :accountId")
    suspend fun deleteCredential(accountId: String)

    // ---- 快照 ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshot(s: LatestQuotaSnapshotEntity)

    @Query("SELECT * FROM latest_quota_snapshot WHERE accountId = :accountId")
    suspend fun getLastSnapshot(accountId: String): LatestQuotaSnapshotEntity?

    @Query("SELECT * FROM latest_quota_snapshot")
    fun observeAllSnapshots(): Flow<List<LatestQuotaSnapshotEntity>>

    @Query("SELECT * FROM latest_quota_snapshot WHERE accountId = :accountId")
    fun observeSnapshot(accountId: String): Flow<LatestQuotaSnapshotEntity?>

    @Query("DELETE FROM latest_quota_snapshot WHERE accountId = :accountId")
    suspend fun deleteSnapshot(accountId: String)

    // ---- 桶 ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBuckets(buckets: List<QuotaBucketEntity>)

    @Query("SELECT * FROM quota_bucket WHERE snapshotAccountId = :accountId ORDER BY resetAt ASC")
    fun observeBuckets(accountId: String): Flow<List<QuotaBucketEntity>>

    @Query("SELECT * FROM quota_bucket WHERE snapshotAccountId = :accountId")
    suspend fun getBuckets(accountId: String): List<QuotaBucketEntity>

    @Query("SELECT * FROM quota_bucket ORDER BY snapshotAccountId, resetAt ASC")
    fun observeAllBuckets(): Flow<List<QuotaBucketEntity>>

    @Query("DELETE FROM quota_bucket WHERE snapshotAccountId = :accountId")
    suspend fun deleteBuckets(accountId: String)

    @Query("DELETE FROM quota_bucket WHERE snapshotAccountId IN (SELECT id FROM provider_account WHERE enabled = 0)")
    suspend fun cleanupDisabledBuckets()
}