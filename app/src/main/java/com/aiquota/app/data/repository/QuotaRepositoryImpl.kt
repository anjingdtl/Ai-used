package com.aiquota.app.data.repository

import com.aiquota.app.data.database.dao.AccountDao
import com.aiquota.app.data.database.dao.EventDao
import com.aiquota.app.data.database.dao.QuotaDao
import com.aiquota.app.data.database.entity.SyncEventEntity
import com.aiquota.app.core.network.toQueryError
import com.aiquota.app.data.mapper.Mapper
import com.aiquota.app.domain.model.DataSource
import com.aiquota.app.domain.model.ProviderState
import com.aiquota.app.domain.model.QueryError
import com.aiquota.app.domain.model.QuotaSnapshot
import com.aiquota.app.domain.model.SyncStatus
import com.aiquota.app.domain.repository.HistoryRepository
import com.aiquota.app.domain.repository.QuotaRepository
import com.aiquota.app.provider.ProviderRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import java.time.Instant
import javax.inject.Inject

/**
 * 额度仓储（Last Known Good Snapshot 核心）：
 * - observe* 完全由本地缓存驱动，断网立即有值；
 * - refresh* 触发网络查询，成功才覆盖缓存，失败保留历史并记录状态。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QuotaRepositoryImpl @Inject constructor(
    private val accountDao: AccountDao,
    private val quotaDao: QuotaDao,
    private val eventDao: EventDao,
    private val historyRepository: HistoryRepository,
    private val providerRegistry: ProviderRegistry
) : QuotaRepository {

    override fun observeEnabledStates(): Flow<List<ProviderState>> =
        accountDao.observeAll().flatMapLatest { accounts ->
            val enabled = accounts.filter { it.enabled }
            if (enabled.isEmpty()) return@flatMapLatest kotlinx.coroutines.flow.flowOf(emptyList())
            combine(quotaDao.observeAllSnapshots(), quotaDao.observeAllBuckets()) { snaps, buckets ->
                enabled.map { acct ->
                    val ent = snaps.firstOrNull { it.accountId == acct.id }
                    val bucketEnts = buckets.filter { it.snapshotAccountId == acct.id }
                    val snapshot = ent?.let { Mapper.toSnapshot(it, bucketEnts) }
                    val status = when {
                        ent == null -> SyncStatus.FAILED_NO_CACHE
                        ent.source == DataSource.CACHE.name -> SyncStatus.CACHED
                        else -> SyncStatus.LIVE
                    }
                    ProviderState(
                        accountId = acct.id,
                        snapshot = snapshot,
                        syncStatus = status,
                        lastSuccessAt = ent?.let { Instant.ofEpochMilli(it.cachedAt) }
                    )
                }
            }
        }

    override fun observeState(accountId: String): Flow<ProviderState> =
        accountDao.observeById(accountId).flatMapLatest { acct ->
            if (acct == null) return@flatMapLatest kotlinx.coroutines.flow.flowOf(
                ProviderState(accountId = accountId)
            )
            combine(quotaDao.observeSnapshot(accountId), quotaDao.observeBuckets(accountId)) { ent, bucketEnts ->
                val snapshot = ent?.let { Mapper.toSnapshot(it, bucketEnts) }
                val status = when {
                    ent == null -> SyncStatus.FAILED_NO_CACHE
                    ent.source == DataSource.CACHE.name -> SyncStatus.CACHED
                    else -> SyncStatus.LIVE
                }
                ProviderState(
                    accountId = accountId,
                    snapshot = snapshot,
                    syncStatus = status,
                    lastSuccessAt = ent?.let { Instant.ofEpochMilli(it.cachedAt) }
                )
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
            recordSync(accountId, SyncStatus.FAILED_NO_CACHE, "未注册平台 ${account.providerId}")
            return
        }
        if (account.connectorType == com.aiquota.app.domain.model.ProviderAccount.ConnectorType.UNAVAILABLE) {
            recordSync(accountId, SyncStatus.UNAVAILABLE, null)
            return
        }
        val hadCache = getLastGoodSnapshot(accountId) != null
        val snapshot = try {
            provider.fetchQuota(account)
        } catch (e: QueryError.ProviderUnavailable) {
            recordSync(accountId, SyncStatus.UNAVAILABLE, e.userMessage())
            return
        } catch (e: QueryError) {
            recordSync(accountId, if (hadCache) SyncStatus.CACHED else SyncStatus.FAILED_NO_CACHE, e.userMessage())
            return
        } catch (e: Exception) {
            val qe = e.toQueryError()
            recordSync(accountId, if (hadCache) SyncStatus.CACHED else SyncStatus.FAILED_NO_CACHE, qe.userMessage())
            return
        }
        persistSnapshot(snapshot)
        val bucket = snapshot.buckets.firstOrNull { it.isKeyBucket }
        if (bucket != null) {
            historyRepository.record(snapshot)
        }
        recordSync(accountId, SyncStatus.LIVE, null)
    }

    override suspend fun persistSnapshot(snapshot: QuotaSnapshot) {
        quotaDao.upsertSnapshot(Mapper.toEntity(snapshot))
        quotaDao.deleteBuckets(snapshot.accountId)
        if (snapshot.buckets.isNotEmpty()) {
            quotaDao.insertBuckets(snapshot.buckets.map { Mapper.toEntity(it, snapshot.accountId) })
        }
    }

    private suspend fun recordSync(accountId: String, status: SyncStatus, message: String?) {
        eventDao.insertSyncEvent(
            SyncEventEntity(
                accountId = accountId,
                status = status.name,
                errorMessage = message,
                at = Instant.now().toEpochMilli()
            )
        )
    }
}