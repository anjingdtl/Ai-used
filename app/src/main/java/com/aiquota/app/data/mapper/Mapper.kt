package com.aiquota.app.data.mapper

import com.aiquota.app.data.database.entity.CredentialEntity
import com.aiquota.app.data.database.entity.LatestQuotaSnapshotEntity
import com.aiquota.app.data.database.entity.ProviderAccountEntity
import com.aiquota.app.data.database.entity.QuotaBucketEntity
import com.aiquota.app.data.database.entity.QuotaHistoryEntity
import com.aiquota.app.data.database.entity.SyncEventEntity
import com.aiquota.app.domain.model.Balance
import com.aiquota.app.domain.model.CredentialType
import com.aiquota.app.domain.model.DataSource
import com.aiquota.app.domain.model.ProviderAccount
import com.aiquota.app.domain.model.QuotaBucket
import com.aiquota.app.domain.model.QuotaSnapshot
import com.aiquota.app.domain.model.QuotaType
import com.aiquota.app.domain.model.SyncStatus
import com.aiquota.app.domain.model.WindowType
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.time.Instant

object Mapper {

    // ---- Account ----
    fun toAccount(e: ProviderAccountEntity) = ProviderAccount(
        id = e.id,
        providerId = e.providerId,
        displayName = e.displayName,
        connectorType = e.connectorType,
        enabled = e.enabled,
        createdAt = Instant.ofEpochMilli(e.createdAt),
        updatedAt = Instant.ofEpochMilli(e.updatedAt)
    )

    fun toEntity(a: ProviderAccount) = ProviderAccountEntity(
        id = a.id,
        providerId = a.providerId,
        displayName = a.displayName,
        connectorType = a.connectorType,
        enabled = a.enabled,
        createdAt = a.createdAt.toEpochMilli(),
        updatedAt = a.updatedAt.toEpochMilli()
    )

    // ---- Snapshot / Bucket ----
    fun toSnapshot(e: LatestQuotaSnapshotEntity, buckets: List<QuotaBucketEntity>): QuotaSnapshot {
        val balance = e.balanceJson?.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString<Balance>(it) }.getOrNull() }
        return QuotaSnapshot(
            id = e.accountId,
            providerId = e.providerId,
            accountId = e.accountId,
            accountName = e.accountName,
            planName = e.planName.orEmpty().takeIf { it.isNotBlank() },
            buckets = buckets.map { toBucket(it) },
            balance = balance,
            queriedAt = Instant.ofEpochMilli(e.queriedAt),
            cachedAt = Instant.ofEpochMilli(e.cachedAt),
            source = e.source.let { runCatching { DataSource.valueOf(it) }.getOrDefault(DataSource.CACHE) }
        )
    }

    fun toBucket(e: QuotaBucketEntity) = QuotaBucket(
        id = e.id,
        name = e.name,
        type = e.type.let { runCatching { QuotaType.valueOf(it) }.getOrDefault(QuotaType.PERCENT) },
        used = e.used,
        limit = e.limit,
        remaining = e.remaining,
        usedPercent = e.usedPercent,
        remainingPercent = e.remainingPercent,
        unit = e.unit,
        windowType = e.windowType.let { runCatching { WindowType.valueOf(it) }.getOrDefault(WindowType.CUSTOM) },
        windowStartAt = e.windowStartAt?.let { Instant.ofEpochMilli(it) },
        resetAt = e.resetAt?.let { Instant.ofEpochMilli(it) }
    )

    fun toEntity(snapshot: QuotaSnapshot): LatestQuotaSnapshotEntity {
        val balanceJson = snapshot.balance?.let { json.encodeToString(Balance.serializer(), it) }
        return LatestQuotaSnapshotEntity(
            accountId = snapshot.accountId,
            providerId = snapshot.providerId,
            accountName = snapshot.accountName,
            planName = snapshot.planName,
            queriedAt = snapshot.queriedAt.toEpochMilli(),
            cachedAt = snapshot.cachedAt.toEpochMilli(),
            source = snapshot.source.name,
            balanceJson = balanceJson,
            minRemainingPercent = snapshot.minRemainingPercent
        )
    }

    fun toEntity(b: QuotaBucket, snapshotAccountId: String) = QuotaBucketEntity(
        id = b.id,
        snapshotAccountId = snapshotAccountId,
        name = b.name,
        type = b.type.name,
        used = b.used,
        limit = b.limit,
        remaining = b.remaining,
        usedPercent = b.usedPercent,
        remainingPercent = b.remainingPercent,
        unit = b.unit,
        windowType = b.windowType.name,
        windowStartAt = b.windowStartAt?.toEpochMilli(),
        resetAt = b.resetAt?.toEpochMilli()
    )

    fun toHistory(e: QuotaHistoryEntity) = com.aiquota.app.domain.repository.QuotaHistoryPoint(
        id = e.id,
        accountId = e.accountId,
        bucketId = e.bucketId,
        remainingPercent = e.remainingPercent,
        remaining = e.remaining,
        recordedAt = Instant.ofEpochMilli(e.recordedAt)
    )

    fun toSyncEvent(e: SyncEventEntity) = com.aiquota.app.domain.repository.SyncEvent(
        accountId = e.accountId,
        status = e.status.let { runCatching { SyncStatus.valueOf(it) }.getOrDefault(SyncStatus.FAILED_NO_CACHE) },
        errorMessage = e.errorMessage,
        at = Instant.ofEpochMilli(e.at)
    )

    fun toCredentialEntity(ref: String, accountId: String, type: CredentialType, blob: String) =
        CredentialEntity(ref, accountId, type.name, blob)

    private val json = Json { ignoreUnknownKeys = true }
}