package com.aiquota.app.data.repository

import com.aiquota.app.data.database.dao.AccountDao
import com.aiquota.app.data.database.dao.EventDao
import com.aiquota.app.data.database.dao.QuotaDao
import com.aiquota.app.data.database.entity.LatestQuotaSnapshotEntity
import com.aiquota.app.data.database.entity.QuotaBucketEntity
import com.aiquota.app.data.database.entity.SyncEventEntity
import com.aiquota.app.core.network.toQueryError
import com.aiquota.app.data.mapper.Mapper
import com.aiquota.app.domain.model.ProviderAccount
import com.aiquota.app.domain.model.ProviderExecutionContext
import com.aiquota.app.domain.model.ProviderState
import com.aiquota.app.domain.model.QueryError
import com.aiquota.app.domain.model.QueryErrorCode
import com.aiquota.app.domain.model.QuotaSnapshot
import com.aiquota.app.domain.model.SyncStatus
import com.aiquota.app.domain.repository.CredentialStore
import com.aiquota.app.domain.repository.HistoryRepository
import com.aiquota.app.domain.repository.QuotaRepository
import com.aiquota.app.provider.ProviderRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import java.time.Instant
import javax.inject.Inject

/**
 * 额度仓储（Last Known Good Snapshot 核心）。
 *
 * 状态模型三要素：
 *  - snapshot（Room）：最后一次成功的数据，只回答“最后成功值是什么”；
 *  - latestSyncEvent（Room）：最近一次同步的 terminal 结果（LIVE/CACHED/FAILED_NO_CACHE/UNAVAILABLE）；
 *  - inFlightAccounts（内存）：是否正在刷新（SYNCING，不落库）。
 *
 * observe* 把这三者 combine 得到 ProviderState。SYNCING 是瞬态、仅存内存，
 * 因此 App 在刷新中被杀死不会让下次启动永远显示 SYNCING。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QuotaRepositoryImpl @Inject constructor(
    private val accountDao: AccountDao,
    private val quotaDao: QuotaDao,
    private val eventDao: EventDao,
    private val historyRepository: HistoryRepository,
    private val providerRegistry: ProviderRegistry,
    private val credentialStore: CredentialStore
) : QuotaRepository {

    /** 内存中“正在刷新”的账号集合（SYNCING 瞬态，不落库）。 */
    private val inFlightAccounts = MutableStateFlow<Set<String>>(emptySet())

    override fun observeEnabledStates(): Flow<List<ProviderState>> =
        accountDao.observeAll().flatMapLatest { accounts ->
            val enabled = accounts.filter { it.enabled }
            if (enabled.isEmpty()) {
                return@flatMapLatest flowOf(emptyList())
            }
            combine(
                quotaDao.observeAllSnapshots(),
                quotaDao.observeAllBuckets(),
                eventDao.observeLatestSyncEvents(),
                inFlightAccounts
            ) { snaps, buckets, latestEvents, inFlight ->
                val eventById = latestEvents.associateBy { it.accountId }
                enabled.map { acct ->
                    buildState(acct.id, snaps, buckets, eventById[acct.id], inFlight)
                }
            }
        }

    override fun observeState(accountId: String): Flow<ProviderState> =
        accountDao.observeById(accountId).flatMapLatest { acct ->
            if (acct == null) return@flatMapLatest flowOf(ProviderState(accountId = accountId))
            combine(
                quotaDao.observeSnapshot(accountId),
                quotaDao.observeBuckets(accountId),
                eventDao.observeLatestSyncEvent(accountId),
                inFlightAccounts
            ) { ent, bucketEnts, latestEvent, inFlight ->
                buildState(accountId, listOfNotNull(ent), bucketEnts, latestEvent, inFlight)
            }
        }

    override suspend fun getLastGoodSnapshot(accountId: String): QuotaSnapshot? {
        val ent = quotaDao.getLastSnapshot(accountId) ?: return null
        return Mapper.toSnapshot(ent, quotaDao.getBuckets(accountId))
    }

    override suspend fun refreshAll() {
        accountDao.getAll()
            .filter { it.enabled }
            .forEach { refresh(Mapper.toAccount(it).id) }
    }

    override suspend fun refresh(accountId: String) {
        val entity = accountDao.getById(accountId) ?: return
        val account = Mapper.toAccount(entity)
        val provider = providerRegistry.getProvider(account.providerId)
        if (provider == null) {
            recordSync(accountId, SyncStatus.FAILED_NO_CACHE, QueryError.Unknown("未注册平台 ${account.providerId}"))
            return
        }
        if (account.connectorType == ProviderAccount.ConnectorType.UNAVAILABLE) {
            recordSync(accountId, SyncStatus.UNAVAILABLE, null)
            return
        }
        inFlightAccounts.update { it + accountId }
        try {
            val credential = credentialStore.load(accountId)
            val context = ProviderExecutionContext(account = account, credential = credential)
            val hadCache = getLastGoodSnapshot(accountId) != null
            val snapshot = try {
                provider.fetchQuota(context)
            } catch (e: QueryError.ProviderUnavailable) {
                recordSync(accountId, SyncStatus.UNAVAILABLE, QueryError.ProviderUnavailable)
                return
            } catch (e: QueryError) {
                recordSync(accountId, if (hadCache) SyncStatus.CACHED else SyncStatus.FAILED_NO_CACHE, e)
                return
            } catch (e: Exception) {
                val qe = e.toQueryError()
                recordSync(accountId, if (hadCache) SyncStatus.CACHED else SyncStatus.FAILED_NO_CACHE, qe)
                return
            }
            persistSnapshot(snapshot)
            val bucket = snapshot.buckets.firstOrNull { it.isKeyBucket }
            if (bucket != null) {
                historyRepository.record(snapshot)
            }
            recordSync(accountId, SyncStatus.LIVE, null)
        } finally {
            inFlightAccounts.update { it - accountId }
        }
    }

    override suspend fun persistSnapshot(snapshot: QuotaSnapshot) {
        quotaDao.upsertSnapshot(Mapper.toEntity(snapshot))
        quotaDao.deleteBuckets(snapshot.accountId)
        if (snapshot.buckets.isNotEmpty()) {
            quotaDao.insertBuckets(snapshot.buckets.map { Mapper.toEntity(it, snapshot.accountId) })
        }
    }

    // ---- 状态装配 ----
    private fun buildState(
        accountId: String,
        snapEnts: List<LatestQuotaSnapshotEntity>,
        bucketEnts: List<QuotaBucketEntity>,
        latestEvent: SyncEventEntity?,
        inFlight: Set<String>
    ): ProviderState {
        val ent = snapEnts.firstOrNull { it.accountId == accountId }
        val snapshot = ent?.let { Mapper.toSnapshot(it, bucketEnts.filter { b -> b.snapshotAccountId == accountId }) }
        val isSyncing = accountId in inFlight
        val latestStatus = latestEvent
            ?.let { runCatching { SyncStatus.valueOf(it.status) }.getOrNull() }
        val status = when {
            isSyncing -> SyncStatus.SYNCING
            latestStatus != null -> latestStatus
            else -> SyncStatus.FAILED_NO_CACHE
        }
        return ProviderState(
            accountId = accountId,
            snapshot = snapshot,
            syncStatus = status,
            lastAttemptAt = latestEvent?.at?.let { Instant.ofEpochMilli(it) },
            lastSuccessAt = ent?.let { Instant.ofEpochMilli(it.cachedAt) },
            error = latestEvent?.errorMessage?.let { QueryErrorCode.decode(it) }
        )
    }

    private suspend fun recordSync(accountId: String, status: SyncStatus, error: QueryError?) {
        eventDao.insertSyncEvent(
            SyncEventEntity(
                accountId = accountId,
                status = status.name,
                errorMessage = error?.let { QueryErrorCode.encode(it) },
                at = Instant.now().toEpochMilli()
            )
        )
    }
}