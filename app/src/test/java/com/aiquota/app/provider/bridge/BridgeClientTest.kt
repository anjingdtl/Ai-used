package com.aiquota.app.provider.bridge

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * P0-8 / P0-11：BridgeClient 网络行为契约测试。
 * 覆盖：成功解析、401/5xx 错误结构化、非法 JSON、超时映射。
 * 所有请求都走 Dispatchers.IO，Response 在网络层自动关闭（见 executeJson 内 use{}）。
 */
class BridgeClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: BridgeClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = BridgeClient(
            baseUrl = server.url("/").toString(),
            client = OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .build()
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `health parses success`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok","version":"1.0"}"""))
        val health = client.health()
        assertEquals("ok", health.status)
    }

    @Test
    fun `providers parses list`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"providers":[{"provider":"glm","label":"智谱 GLM","supportsQuota":true}]}""")
        )
        val providers = client.providers()
        assertEquals(1, providers.size)
        assertEquals("glm", providers[0].provider)
    }

    @Test
    fun `quota success parses`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"provider":"glm","accountId":"acc","source":"bridge",
                   "buckets":[{"id":"b1","name":"积分","windowType":"WEEKLY",
                   "remainingPercent":30.0,"resetAt":"2026-09-15T10:20:30Z"}]}"""
            )
        )
        val quota = client.quota("secret", "glm", "acc")
        assertEquals("glm", quota.provider)
        assertEquals(1, quota.buckets.size)
    }

    @Test
    fun `http 401 throws bridge protocol exception with code`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"bad secret"}"""))
        val e = try {
            client.quota("wrong", "glm", "acc")
            null
        } catch (ex: BridgeProtocolException) {
            ex
        }
        assertEquals(401, e?.statusCode)
    }

    @Test
    fun `http 500 throws bridge protocol exception with code`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        val e = try {
            client.quota("s", "glm", "acc")
            null
        } catch (ex: BridgeProtocolException) {
            ex
        }
        assertEquals(500, e?.statusCode)
    }

    @Test
    fun `invalid json throws serialization exception`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not-json"))
        var threw = false
        try {
            client.health()
        } catch (e: Exception) {
            threw = e is kotlinx.serialization.SerializationException
        }
        assertEquals(true, threw)
    }

    @Test
    fun `unreachable maps to CONNECT code`() = runTest {
        // 关闭 server 后请求 -> IOException -> BridgeProtocolException(CONNECT)
        server.shutdown()
        val e = try {
            client.health()
            null
        } catch (ex: BridgeProtocolException) {
            ex
        }
        assertEquals(BridgeProtocolException.CONNECT, e?.statusCode)
    }
}