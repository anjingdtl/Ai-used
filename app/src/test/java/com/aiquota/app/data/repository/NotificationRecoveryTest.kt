package com.aiquota.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiquota.app.TestRobolectricApplication
import com.aiquota.app.data.database.AiQuotaDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * P1-2：通知恢复逻辑 —— 额度恢复后必须清除旧阈值状态，允许再次跌破时重新提醒。
 * 场景：30% 通知启动 -> 未恢复不重复 -> 恢复清除 -> 再次跌破允许重新通知。
 * 这里验证 Repository 层原语，Worker 层的编排逻辑见 QuotaSyncWorker。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestRobolectricApplication::class)
class NotificationRecoveryTest {

    private lateinit var db: AiQuotaDatabase
    private lateinit var repo: NotificationRepositoryImpl

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AiQuotaDatabase::class.java).build()
        repo = NotificationRepositoryImpl(db.eventDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun key(account: String = "glm-acc1", bucket: String = "glm-weekly") = "$account:$bucket"

    @Test
    fun `30触发后未恢复_不重复通知`() = runBlocking {
        val k = key()
        repo.markNotified(k, 30)
        // 额度 25% 仍 <= 30，保持已通知（不重复）
        repo.evaluate(k, 30, 25.0)
        assertTrue(repo.isNotified(k, 30))
    }

    @Test
    fun `80恢复后_清除标记_可再次通知`() = runBlocking {
        val k = key()
        repo.markNotified(k, 30)
        repo.evaluate(k, 30, 80.0) // 恢复
        assertFalse(repo.isNotified(k, 30))
        // 之后又可再次 markNotified（下次跌破重新提醒）
        repo.markNotified(k, 30)
        assertTrue(repo.isNotified(k, 30))
    }

    @Test
    fun `阈值边界_恰好等于阈值_视为未恢复`() = runBlocking {
        val k = key()
        repo.markNotified(k, 30)
        repo.evaluate(k, 30, 30.0) // 未高于阈值，不算恢复
        assertTrue(repo.isNotified(k, 30))
    }

    @Test
    fun `null百分比_视为未恢复_不清除`() = runBlocking {
        val k = key()
        repo.markNotified(k, 20)
        repo.evaluate(k, 20, null)
        assertTrue(repo.isNotified(k, 20))
    }
}