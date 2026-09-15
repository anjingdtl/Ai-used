package com.aiquota.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiquota.app.TestRobolectricApplication
import com.aiquota.app.data.database.AiQuotaDatabase
import com.aiquota.app.data.database.entity.QuotaHistoryEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * P1-3：History prune 真正执行 —— 按保留窗口删除过期点。
 * 验证 now - retentionDays 之后的数据被清理、窗口内的数据被保留。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestRobolectricApplication::class)
class HistoryPruneTest {

    private lateinit var db: AiQuotaDatabase
    private lateinit var repo: HistoryRepositoryImpl

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AiQuotaDatabase::class.java).build()
        repo = HistoryRepositoryImpl(db.historyDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `prune 只删除保留窗口外的点`() = runBlocking {
        val now = Instant.now()
        val old = now.minus(30, ChronoUnit.DAYS).toEpochMilli()
        val fresh = now.minus(1, ChronoUnit.DAYS).toEpochMilli()

        db.historyDao().insert(
            QuotaHistoryEntity(accountId = "glm-acc", bucketId = "glm-weekly", remainingPercent = 30.0, remaining = 70.0, recordedAt = old)
        )
        db.historyDao().insert(
            QuotaHistoryEntity(accountId = "glm-acc", bucketId = "glm-weekly", remainingPercent = 90.0, remaining = 10.0, recordedAt = fresh)
        )
        assertEquals(2, db.historyDao().count())

        // 保留 14 天
        val keepSince = now.minus(14, ChronoUnit.DAYS)
        repo.prune(keepSince)

        assertEquals(1, db.historyDao().count())
        val remaining = db.historyDao().latest("glm-acc", "glm-weekly")
        assertEquals(fresh, remaining?.recordedAt)
    }

    @Test
    fun `边界_恰在窗口起点_被保留`() = runBlocking {
        val now = Instant.now()
        val boundary = now.minus(7, ChronoUnit.DAYS).toEpochMilli()
        db.historyDao().insert(
            QuotaHistoryEntity(accountId = "glm-acc", bucketId = "glm-weekly", remainingPercent = 50.0, remaining = 50.0, recordedAt = boundary)
        )
        // keepSince == recordedAt（不早于），应保留
        repo.prune(now.minus(7, ChronoUnit.DAYS))
        assertEquals(1, db.historyDao().count())
    }
}