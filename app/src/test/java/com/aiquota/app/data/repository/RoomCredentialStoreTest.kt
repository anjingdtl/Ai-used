package com.aiquota.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiquota.app.TestRobolectricApplication
import com.aiquota.app.core.security.SecureCipherStore
import com.aiquota.app.core.security.TestAesKeySource
import com.aiquota.app.data.database.AiQuotaDatabase
import com.aiquota.app.domain.model.CredentialType
import com.aiquota.app.domain.model.ProviderCredential
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Credential 链测试：保存 -> 重新创建 Store -> 读取 -> 内容一致。
 * 验证明文的 Secret 绝不会直接落库（只存加密 blob），进程重启后可读取。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestRobolectricApplication::class)
class RoomCredentialStoreTest {

    private lateinit var db: AiQuotaDatabase
    private val secure = SecureCipherStore(alias = "cred_test_keystore_" + System.nanoTime(), keySource = TestAesKeySource)

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AiQuotaDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun newStore() = RoomCredentialStore(db.quotaDao(), secure)

    @Test
    fun `保存后读取_内容一致`() = runBlocking {
        val credential = ProviderCredential(
            providerId = "glm",
            type = CredentialType.BRIDGE,
            secret = "sk-real-bridge-secret-abcdef",
            extra = mapOf("bridgeUrl" to "https://127.0.0.1:8787", "bridgeScheme" to "https")
        )
        newStore().save("acc1", credential)

        val loaded = newStore().load("acc1")
        assertEquals(credential.providerId, loaded?.providerId)
        assertEquals(credential.type, loaded?.type)
        assertEquals(credential.secret, loaded?.secret)
        assertEquals(credential.extra, loaded?.extra)
    }

    @Test
    fun `重新创建Store后_仍能读取_验证进程重启可用`() = runBlocking {
        val credential = ProviderCredential(
            providerId = "openai_codex",
            type = CredentialType.API_KEY,
            secret = "sk-codex-token-xyz",
            extra = emptyMap()
        )
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val dbPath = ctx.cacheDir.resolve("cred_persist_${System.nanoTime()}.db")
        // 进程 A：写入真实磁盘数据库
        var fileDb = Room.databaseBuilder(ctx, AiQuotaDatabase::class.java, dbPath.absolutePath).build()
        val storeA = RoomCredentialStore(fileDb.quotaDao(), secure)
        storeA.save("acc2", credential)
        // 关闭整个数据库，模拟进程退出
        fileDb.close()
        // 进程 B：重新打开同一磁盘数据库
        fileDb = Room.databaseBuilder(ctx, AiQuotaDatabase::class.java, dbPath.absolutePath).build()
        val storeB = RoomCredentialStore(fileDb.quotaDao(), secure)
        assertEquals(credential.secret, storeB.load("acc2")?.secret)
        fileDb.close()
        dbPath.delete()
        Unit
    }

    @Test
    fun `未保存的账号_返回null`() = runBlocking {
        assertNull(newStore().load("missing"))
    }

    @Test
    fun `删除后_无法读取`() = runBlocking {
        newStore().save("acc3", ProviderCredential("glm", CredentialType.BRIDGE, "sk-del"))
        newStore().delete("acc3")
        assertNull(newStore().load("acc3"))
    }

    @Test
    fun `明文secret_不直接落库`() = runBlocking {
        val secret = "sk-topsecret-1234567890"
        newStore().save("acc4", ProviderCredential("minimax", CredentialType.BRIDGE, secret))
        val entity = db.quotaDao().getCredential("acc4")
        // 落库的是加密 blob，不含明文 secret
        assertEquals(false, entity?.encryptedBlob?.contains(secret))
    }
}