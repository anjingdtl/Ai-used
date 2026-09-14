package com.aiquota.app.data.mapper

import com.aiquota.app.data.database.entity.QuotaBucketEntity
import com.aiquota.app.domain.model.Balance
import com.aiquota.app.domain.model.DataSource
import com.aiquota.app.domain.model.QuotaBucket
import com.aiquota.app.domain.model.QuotaSnapshot
import com.aiquota.app.domain.model.QuotaType
import com.aiquota.app.domain.model.WindowType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class MapperTest {

    @Test
    fun `快照往返_保留关键字段`() {
        val snapshot = QuotaSnapshot(
            id = "acc1", providerId = "glm", accountId = "acc1", accountName = "GLM",
            planName = "Coding Plan",
            buckets = listOf(
                QuotaBucket(
                    id = "weekly", name = "每周限额", type = QuotaType.PERCENT,
                    used = 5.0, limit = 20.0, remaining = 15.0,
                    usedPercent = 25.0, remainingPercent = 75.0, unit = null,
                    windowType = WindowType.WEEKLY,
                    resetAt = Instant.ofEpochMilli(1_700_000_000_000)
                )
            ),
            balance = Balance(
                id = "b", name = "余额", available = 12.34, currency = "USD", unit = "USD",
                updatedAt = Instant.ofEpochMilli(1_700_000_000_001)
            ),
            queriedAt = Instant.ofEpochMilli(1_700_000_000_000),
            cachedAt = Instant.ofEpochMilli(1_700_000_000_002),
            source = DataSource.NETWORK
        )

        val entity = Mapper.toEntity(snapshot)
        val bucketEntity = Mapper.toEntity(snapshot.buckets.first(), "acc1")
        val restored = Mapper.toSnapshot(entity, listOf(bucketEntity))

        assertEquals("acc1", restored.accountId)
        assertEquals("glm", restored.providerId)
        assertEquals("Coding Plan", restored.planName)
        assertEquals(1, restored.buckets.size)

        val bucket = restored.buckets.first()
        assertEquals(15.0, bucket.remaining!!, 0.001)
        assertEquals(75.0, bucket.remainingPercent!!, 0.0001)
        assertEquals(WindowType.WEEKLY, bucket.windowType)
        assertEquals(1_700_000_000_000, bucket.resetAt?.toEpochMilli())

        assertNotNull(restored.balance)
        assertEquals(12.34, restored.balance!!.available, 0.001)
        assertEquals("USD", restored.balance!!.currency)
        assertEquals(DataSource.NETWORK, restored.source)
    }

    @Test
    fun `空planName_映射为空串`() {
        val entity = Mapper.toEntity(
            QuotaSnapshot(
                id = "a", providerId = "grok", accountId = "a", accountName = "x",
                planName = null, buckets = emptyList(),
                queriedAt = Instant.EPOCH, cachedAt = Instant.EPOCH, source = DataSource.BRIDGE
            )
        )
        val restored = Mapper.toSnapshot(entity, emptyList())
        assertNull(restored.planName)
        assertNull(restored.minRemainingPercent)
    }

    @Test
    fun `未知WindowType_回退为CUSTOM`() {
        val entity = QuotaBucketEntity(
            id = "x", snapshotAccountId = "a", name = "桶", type = "PERCENT",
            used = null, limit = null, remaining = null, usedPercent = null, remainingPercent = null,
            unit = null, windowType = "NOT_A_REAL_TYPE", windowStartAt = null, resetAt = null
        )
        assertEquals(WindowType.CUSTOM, Mapper.toBucket(entity).windowType)
    }
}