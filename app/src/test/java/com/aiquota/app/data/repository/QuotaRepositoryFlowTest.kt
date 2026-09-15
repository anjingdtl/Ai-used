package com.aiquota.app.data.repository

import app.cash.turbine.test
import com.aiquota.app.MainDispatcherRule
import com.aiquota.app.data.database.dao.AccountDao
import com.aiquota.app.data.database.dao.EventDao
import com.aiquota.app.data.database.dao.QuotaDao
import com.aiquota.app.data.database.entity.LatestQuotaSnapshotEntity
import com.aiquota.app.data.database.entity.ProviderAccountEntity
import com.aiquota.app.data.database.entity.QuotaBucketEntity
import com.aiquota.app.data.database.entity.SyncEventEntity
import com.aiquota.app.domain.model.DataSource
import com.aiquota.app.domain.model.ProviderAccount
import com.aiquota.app.domain.model.QueryError
import com.aiquota.app.domain.model.QueryErrorCode
import com.aiquota.app.domain.model.SyncStatus
import com.aiquota.app.domain.repository.CredentialStore
import com.aiquota.app.domain.repository.HistoryRepository
import com.aiquota.app.domain.repository.QuotaProvider
import com.aiquota.app.provider.ProviderRegistry
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * Last Known Good 状态流测试：
 * 验证 UI 观察到的 ProviderState 由最新同步事件 + snapshot 联合推导，
 * 网络失败时保留旧快照并切换到 CACHED，网络恢复回到 LIVE。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QuotaRepositoryFlowTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val accountDao = mockk<AccountDao>()
    private val quotaDao = mockk<QuotaDao>()
    private val eventDao = mockk<EventDao>()
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

    private val snapshotFlow = MutableStateFlow<LatestQuotaSnapshotEntity?>(null)
    private val bucketFlow = MutableStateFlow<List<QuotaBucketEntity>>(emptyList())
    private val eventFlow = MutableStateFlow<SyncEventEntity?>(null)

    @Before
    fun setUp() {
        repo = QuotaRepositoryImpl(accountDao, quotaDao, eventDao, historyRepository, providerRegistry, credentialStore)
        every { accountDao.observeById("acc1") } returns MutableStateFlow(accountEntity)
        coEvery { accountDao.getById("acc1") } returns accountEntity
        every { quotaDao.observeSnapshot("acc1") } returns snapshotFlow
        every { quotaDao.observeBuckets("acc1") } returns bucketFlow
        every { eventDao.observeLatestSyncEvent("acc1") } returns eventFlow
        coEvery { eventDao.insertSyncEvent(any()) } just runs
        coEvery { quotaDao.upsertSnapshot(any()) } just runs
        coEvery { quotaDao.deleteBuckets("acc1") } just runs
        coEvery { quotaDao.insertBuckets(any()) } just runs
        every { providerRegistry.getProvider("glm") } returns provider
        coEvery { quotaDao.getLastSnapshot("acc1") } answers { snapshotFlow.value }
    }

    private fun snapshotEntity(remaining: Double, source: DataSource = DataSource.NETWORK): LatestQuotaSnapshotEntity {
        val now = Instant.now().toEpochMilli()
        bucketFlow.value = listOf(
            QuotaBucketEntity(
                id = "b1", snapshotAccountId = "acc1", name = "每周限额", type = "PERCENT",
                used = 100.0 - remaining, limit = 100.0, remaining = remaining,
                usedPercent = 100.0 - remaining, remainingPercent = remaining,
                unit = "%", windowType = "WEEKLY", windowStartAt = null, resetAt = null
            )
        )
        return LatestQuotaSnapshotEntity(
            accountId = "acc1", providerId = "glm", accountName = "GLM",
            planName = "Coding Plan", queriedAt = now, cachedAt = now,
            source = source.name, balanceJson = null, minRemainingPercent = remaining
        )
    }

    private fun sync(eventStatus: SyncStatus, msg: String? = null) = SyncEventEntity(
        id = 0, accountId = "acc1", status = eventStatus.name,
        errorMessage = msg?.let { QueryErrorCode.encode(QueryError.Timeout) },
        at = Instant.now().toEpochMilli()
    )

    @Test
    fun `无数据_查询失败_为FAILED_NO_CACHE`() = runTest {
        // 无快照 + 无事件 -> 默认 FAILED_NO_CACHE
        repo.observeState("acc1").test {
            val st = awaitItem()
            assertEquals(SyncStatus.FAILED_NO_CACHE, st.syncStatus)
            assertNull(st.snapshot)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `查询成功37_为LIVE且snapshot为37`() = runTest {
        snapshotFlow.value = snapshotEntity(37.0)
        eventFlow.value = sync(SyncStatus.LIVE)

        repo.observeState("acc1").test {
            val st = awaitItem()
            assertEquals(SyncStatus.LIVE, st.syncStatus)
            assertEquals(37.0, st.snapshot?.minRemainingPercent)
        }
    }

    @Test
    fun `下次超时_为CACHED_快照仍37_错误为Timeout`() = runTest {
        snapshotFlow.value = snapshotEntity(37.0)
        eventFlow.value = sync(SyncStatus.LIVE)

        repo.observeState("acc1").test {
            assertEquals(SyncStatus.LIVE, awaitItem().syncStatus)
            // 模拟第二次刷新超时 -> 只更新同步事件为 CACHED，快照保留
            eventFlow.value = sync(SyncStatus.CACHED, "timeout")
            val st = awaitItem()
            assertEquals(SyncStatus.CACHED, st.syncStatus)
            assertEquals(37.0, st.snapshot?.minRemainingPercent)
            assertTrue(st.error is QueryError.Timeout)
        }
    }

    @Test
    fun `恢复成功82_为LIVE_错误清除`() = runTest {
        snapshotFlow.value = snapshotEntity(37.0)
        eventFlow.value = sync(SyncStatus.LIVE)

        repo.observeState("acc1").test {
            assertEquals(SyncStatus.LIVE, awaitItem().syncStatus)
            // 先进入 CACHED
            eventFlow.value = sync(SyncStatus.CACHED, "timeout")
            assertEquals(SyncStatus.CACHED, awaitItem().syncStatus)
            // 网络恢复 -> 新快照 82% + LIVE（并发合并，丢弃中间态直到稳定为 LIVE）
            snapshotFlow.value = snapshotEntity(82.0)
            eventFlow.value = sync(SyncStatus.LIVE)
            var st = awaitItem()
            while (st.syncStatus != SyncStatus.LIVE) { st = awaitItem() }
            assertEquals(SyncStatus.LIVE, st.syncStatus)
            assertEquals(82.0, st.snapshot?.minRemainingPercent)
            assertNull(st.error)
        }
    }
}