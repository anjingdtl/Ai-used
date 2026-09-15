package com.aiquota.app.data.repository

import com.aiquota.app.MainDispatcherRule
import com.aiquota.app.data.database.dao.AccountDao
import com.aiquota.app.data.database.dao.EventDao
import com.aiquota.app.data.database.dao.QuotaDao
import com.aiquota.app.core.network.toQueryError
import com.aiquota.app.data.database.entity.LatestQuotaSnapshotEntity
import com.aiquota.app.data.database.entity.ProviderAccountEntity
import com.aiquota.app.domain.model.DataSource
import com.aiquota.app.domain.model.ProviderAccount
import com.aiquota.app.domain.model.QueryError
import com.aiquota.app.domain.model.QuotaBucket
import com.aiquota.app.domain.model.QuotaSnapshot
import com.aiquota.app.domain.model.QuotaType
import com.aiquota.app.domain.model.SyncStatus
import com.aiquota.app.domain.model.WindowType
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
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class QuotaRepositoryImplTest {

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

    private val account = ProviderAccount(
        id = "acc1", providerId = "glm", displayName = "GLM",
        connectorType = ProviderAccount.ConnectorType.BRIDGE,
        enabled = true, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH
    )

    private fun snapshot(remaining: Double, source: DataSource = DataSource.NETWORK) = QuotaSnapshot(
        id = "acc1", providerId = "glm", accountId = "acc1", accountName = "GLM",
        planName = "Coding Plan",
        buckets = listOf(
            QuotaBucket(
                id = "weekly", name = "每周限额", type = QuotaType.PERCENT,
                remaining = remaining, remainingPercent = remaining,
                windowType = WindowType.WEEKLY
            )
        ),
        queriedAt = Instant.now(), cachedAt = Instant.now(), source = source
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
    fun `首次成功刷新_持久化快照并标记LIVE`() = runTest {
        coEvery { quotaDao.getLastSnapshot("acc1") } returns null
        coEvery { provider.fetchQuota(any()) } returns snapshot(42.0)

        repo.refresh("acc1")

        coVerify { quotaDao.upsertSnapshot(any()) }
        coVerify { historyRepository.record(any()) }
        coVerify { eventDao.insertSyncEvent(match { it.status == SyncStatus.LIVE.name }) }
        coVerify { quotaDao.deleteBuckets("acc1") }
        coVerify { quotaDao.insertBuckets(any()) }
    }

    @Test
    fun `已有缓存时_查询失败_保留历史标记为CACHE`() = runTest {
        coEvery { quotaDao.getLastSnapshot("acc1") } returns cachedEntity
        coEvery { provider.fetchQuota(any()) } answers { throw QueryError.Timeout }

        repo.refresh("acc1")

        coVerify(exactly = 0) { quotaDao.upsertSnapshot(any()) }
        coVerify(exactly = 0) { historyRepository.record(any()) }
        coVerify { eventDao.insertSyncEvent(match { it.status == SyncStatus.CACHED.name }) }
    }

    @Test
    fun `无缓存时_查询失败_标记FAILED_NO_CACHE且不持久化`() = runTest {
        coEvery { quotaDao.getLastSnapshot("acc1") } returns null
        coEvery { provider.fetchQuota(any()) } answers { throw java.net.ConnectException("boom").toQueryError() }

        repo.refresh("acc1")

        coVerify(exactly = 0) { quotaDao.upsertSnapshot(any()) }
        coVerify { eventDao.insertSyncEvent(match { it.status == SyncStatus.FAILED_NO_CACHE.name }) }
    }

    @Test
    fun `UNAVAILABLE平台_不调用查询_记录UNAVAILABLE`() = runTest {
        val unavail = ProviderAccountEntity(
            id = "acc2", providerId = "grok", displayName = "Grok",
            connectorType = ProviderAccount.ConnectorType.UNAVAILABLE,
            enabled = true, createdAt = 0, updatedAt = 0
        )
        coEvery { accountDao.getById("acc2") } returns unavail
        every { providerRegistry.getProvider("grok") } returns provider

        repo.refresh("acc2")

        coVerify(exactly = 0) { provider.fetchQuota(any()) }
        coVerify { eventDao.insertSyncEvent(match { it.status == SyncStatus.UNAVAILABLE.name }) }
    }

    @Test
    fun `ProviderUnavailable_映射为UNAVAILABLE`() = runTest {
        coEvery { quotaDao.getLastSnapshot("acc1") } returns cachedEntity
        coEvery { provider.fetchQuota(any()) } answers { throw QueryError.ProviderUnavailable }

        repo.refresh("acc1")

        coVerify { eventDao.insertSyncEvent(match { it.status == SyncStatus.UNAVAILABLE.name }) }
    }
}