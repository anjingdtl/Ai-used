package com.aiquota.app.data.repository

import com.aiquota.app.MainDispatcherRule
import com.aiquota.app.core.network.toQueryError
import com.aiquota.app.data.database.dao.AccountDao
import com.aiquota.app.data.database.dao.EventDao
import com.aiquota.app.data.database.dao.QuotaDao
import com.aiquota.app.data.database.entity.LatestQuotaSnapshotEntity
import com.aiquota.app.data.database.entity.ProviderAccountEntity
import com.aiquota.app.domain.model.DataSource
import com.aiquota.app.domain.model.ProviderAccount
import com.aiquota.app.domain.model.QueryError
import com.aiquota.app.domain.model.SyncStatus
import com.aiquota.app.domain.repository.CredentialStore
import com.aiquota.app.domain.repository.HistoryRepository
import com.aiquota.app.domain.repository.QuotaProvider
import com.aiquota.app.provider.ProviderRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * P0-3 核心契约：当 Provider 返回 ProviderUnavailable（对应 Bridge unsupported / 空 buckets 响应）时，
 * 绝不能把空数据当成 LIVE 记录，也不能覆盖历史缓存。
 * 有旧缓存 -> 保留缓存并记录 CACHED/UNAVAILABLE；无缓存 -> FAILED_NO_CACHE/UNAVAILABLE，且绝不 upsertSnapshot。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QuotaRepositoryUnsupportedTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val accountDao = mockk<AccountDao>()
    private val quotaDao = mockk<QuotaDao>(relaxed = true)
    private val eventDao = mockk<EventDao>(relaxed = true)
    private val historyRepository = mockk<HistoryRepository>(relaxed = true)
    private val credentialStore = mockk<CredentialStore>(relaxed = true)
    private val providerRegistry = mockk<ProviderRegistry>()
    private val provider = mockk<QuotaProvider>()

    private lateinit var repo: QuotaRepositoryImpl

    private val accountEntity = ProviderAccountEntity(
        id = "acc1", providerId = "glm", displayName = "GLM",
        connectorType = ProviderAccount.ConnectorType.BRIDGE,
        enabled = true, createdAt = 0, updatedAt = 0
    )

    private val cachedEntity = LatestQuotaSnapshotEntity(
        accountId = "acc1", providerId = "glm", accountName = "GLM",
        planName = "Coding Plan", queriedAt = 1000, cachedAt = 1000,
        source = DataSource.NETWORK.name, balanceJson = null, minRemainingPercent = 50.0
    )

    @Before
    fun setUp() {
        repo = QuotaRepositoryImpl(accountDao, quotaDao, eventDao, historyRepository, providerRegistry, credentialStore)
        coEvery { accountDao.getById("acc1") } returns accountEntity
        every { providerRegistry.getProvider("glm") } returns provider
        coEvery { quotaDao.upsertSnapshot(any()) } just runs
        coEvery { quotaDao.deleteBuckets("acc1") } just runs
        coEvery { quotaDao.insertBuckets(any()) } just runs
    }

    @Test
    fun `有旧缓存时_unsupported_保留缓存_绝不标记LIVE`() = runTest {
        coEvery { quotaDao.getLastSnapshot("acc1") } returns cachedEntity
        // BridgeQuotaValidator 见到 unsupported 抛 ProviderUnavailable
        coEvery { provider.fetchQuota(any()) } answers { throw QueryError.ProviderUnavailable }

        repo.refresh("acc1")

        coVerify(exactly = 0) { quotaDao.upsertSnapshot(any()) }       // 不覆盖缓存
        coVerify(exactly = 0) { historyRepository.record(any()) }      // 不产空历史
        coVerify(exactly = 0) { eventDao.insertSyncEvent(match { it.status == SyncStatus.LIVE.name }) }
        coVerify { eventDao.insertSyncEvent(match { it.status == SyncStatus.CACHED.name }) }
    }

    @Test
    fun `无缓存时_unsupported_标记UNAVAILABLE_不LIVE`() = runTest {
        coEvery { quotaDao.getLastSnapshot("acc1") } returns null
        coEvery { provider.fetchQuota(any()) } answers { throw QueryError.ProviderUnavailable }

        repo.refresh("acc1")

        coVerify(exactly = 0) { quotaDao.upsertSnapshot(any()) }
        coVerify(exactly = 0) { eventDao.insertSyncEvent(match { it.status == SyncStatus.LIVE.name }) }
        coVerify { eventDao.insertSyncEvent(match { it.status == SyncStatus.UNAVAILABLE.name }) }
    }

    @Test
    fun `无缓存时_无效响应_标记FAILED_NO_CACHE_不LIVE`() = runTest {
        coEvery { quotaDao.getLastSnapshot("acc1") } returns null
        coEvery { provider.fetchQuota(any()) } answers { throw QueryError.InvalidResponse("empty buckets") }

        repo.refresh("acc1")

        coVerify(exactly = 0) { quotaDao.upsertSnapshot(any()) }
        coVerify(exactly = 0) { eventDao.insertSyncEvent(match { it.status == SyncStatus.LIVE.name }) }
        coVerify { eventDao.insertSyncEvent(match { it.status == SyncStatus.FAILED_NO_CACHE.name }) }
    }

    @Test
    fun `网络全断_映射为连接类错误`() {
        val qe = java.net.ConnectException("refused").toQueryError()
        org.junit.Assert.assertTrue(
            "ConnectException 应映射为 BridgeOffline 或网络类错误，当前=${qe.javaClass.simpleName}",
            qe is QueryError.BridgeOffline || qe is QueryError.NetworkUnavailable
        )
    }
}