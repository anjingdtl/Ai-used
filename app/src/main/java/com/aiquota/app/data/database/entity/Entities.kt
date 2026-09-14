package com.aiquota.app.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.aiquota.app.domain.model.ProviderAccount

@Entity(tableName = "provider_account")
data class ProviderAccountEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val displayName: String,
    val connectorType: ProviderAccount.ConnectorType,
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "credential")
data class CredentialEntity(
    @PrimaryKey val ref: String,
    val accountId: String,
    val credentialType: String,
    val encryptedBlob: String
)

@Entity(tableName = "latest_quota_snapshot")
data class LatestQuotaSnapshotEntity(
    @PrimaryKey val accountId: String,
    val providerId: String,
    val accountName: String,
    val planName: String?,
    val queriedAt: Long,
    val cachedAt: Long,
    val source: String,
    val balanceJson: String?,
    val minRemainingPercent: Double?
)

@Entity(tableName = "quota_bucket")
data class QuotaBucketEntity(
    @PrimaryKey val id: String,
    val snapshotAccountId: String,
    val name: String,
    val type: String,
    val used: Double?,
    val limit: Double?,
    val remaining: Double?,
    val usedPercent: Double?,
    val remainingPercent: Double?,
    val unit: String?,
    val windowType: String,
    val windowStartAt: Long?,
    val resetAt: Long?
)

@Entity(tableName = "quota_history")
data class QuotaHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: String,
    val bucketId: String,
    val remainingPercent: Double?,
    val remaining: Double?,
    val recordedAt: Long
)

@Entity(tableName = "sync_event")
data class SyncEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: String,
    val status: String,
    val errorMessage: String?,
    val at: Long
)

@Entity(tableName = "notification_event")
data class NotificationEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bucketId: String,
    val threshold: Int,
    val createdAt: Long
)

@Entity(tableName = "app_setting")
data class AppSettingEntity(
    @PrimaryKey val key: String,
    val value: String?
)