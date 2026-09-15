package com.aiquota.app.provider.bridge

import com.aiquota.app.core.security.SecretMasker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Quota Bridge 局域网协议客户端。
 *
 * 协议：GET /health、GET /providers、GET /quota、POST /pair
 *
 * 网络约定（P0-8）：
 *  - 所有请求都在 [Dispatchers.IO] 上执行，绝不阻塞主线程；
 *  - 每个 Response 都通过 [kotlin.io.use] 自动关闭，禁止裸持有 okhttp3.Response；
 *  - HTTP 错误抛结构化 [BridgeProtocolException]，由上层精确映射，不依赖正则解析。
 *
 * 注意：BaseHTTPRequestHandler 的 POST /pair 也走 `executeJson`，统一 IO + close。
 */
class BridgeClient(
    private val baseUrl: String,
    private val client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    @Throws(Exception::class)
    suspend fun health(): HealthResponse {
        val raw = executeJson(method = "GET", path = "/health", secret = null)
        return decode(HealthResponse.serializer(), raw, "/health")
    }

    @Throws(Exception::class)
    suspend fun providers(): List<BridgeProviderInfo> {
        val raw = executeJson(method = "GET", path = "/providers", secret = null)
        return decode(BridgeProvidersPayload.serializer(), raw, "/providers").providers
    }

    @Throws(Exception::class)
    suspend fun quota(secret: String, provider: String?, accountId: String?): BridgeQuota {
        val request = Request.Builder()
            .url(url("/quota"))
            .addHeader("Authorization", "Bearer $secret")
            .addHeader("X-Provider", provider ?: "")
            .addHeader("X-Account", accountId ?: "")
            .build()
        val raw = executeJson(request)
        return decode(BridgeQuota.serializer(), raw, "/quota")
    }

    @Throws(Exception::class)
    suspend fun pair(secret: String): PairResult {
        val request = Request.Builder()
            .url(url("/pair"))
            .addHeader("Authorization", "Bearer $secret")
            .build()
        val raw = executeJson(request)
        return decode(PairResult.serializer(), raw, "/pair")
    }

    /** 构造带可选 Bearer 的 GET 请求并执行，返回 body 字符串（已关闭 Response）。 */
    private suspend fun executeJson(method: String, path: String, secret: String?): String {
        val builder = Request.Builder().url(url(path))
        if (method == "GET") builder.get()
        secret?.let { builder.addHeader("Authorization", "Bearer $it") }
        return executeJson(builder.build())
    }

    /**
     * 请求 -> 校验状态码 -> 读取 body -> 关闭 Response -> 返回 body。全程在 IO 线程。
     * 传输层失败（超时 / 连接拒绝 / 普通 IO）统一转结构化 [BridgeProtocolException]。
     */
    private suspend fun executeJson(request: Request): String {
        val endpoint = request.url.toString()
        return try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { resp ->
                    val body = resp.body?.string()
                    if (!resp.isSuccessful) {
                        throw BridgeProtocolException(resp.code, endpoint, body)
                    }
                    body ?: throw BridgeProtocolException(resp.code, endpoint, body)
                }
            }
        } catch (e: BridgeProtocolException) {
            throw e
        } catch (e: java.io.IOException) {
            throw BridgeProtocolException.fromIo(e, endpoint)
        }
    }

    /** 反序列化；失败抛 [kotlinx.serialization.SerializationException]，由上层映射为 InvalidResponse。 */
    private fun <T> decode(serializer: kotlinx.serialization.KSerializer<T>, raw: String, endpoint: String): T {
        if (raw.isBlank()) throw kotlinx.serialization.SerializationException("空响应 @ $endpoint")
        return json.decodeFromString(serializer, raw)
    }

    private fun url(path: String) = baseUrl.trimEnd('/') + path
}

/**
 * Bridge 协议结构化异常。
 * 用 statusCode 表达 HTTP 状态，另以负常量表达传输层失败，避免靠解析异常 message 猜状态码。
 */
class BridgeProtocolException(
    val statusCode: Int,
    val endpoint: String,
    val body: String? = null,
    cause: Throwable? = null
) : Exception("bridge ${statusCode} @ ${SecretMasker.redact(endpoint)}", cause) {
    companion object {
        /** 连接超时 */
        const val TIMEOUT = -1

        /** 连接拒绝 / 普通网络 IO 失败（等价 Bridge 离线） */
        const val CONNECT = -2

        fun fromIo(e: java.io.IOException, endpoint: String): BridgeProtocolException =
            when (e) {
                is java.net.SocketTimeoutException ->
                    BridgeProtocolException(TIMEOUT, endpoint, cause = e)
                else ->
                    BridgeProtocolException(CONNECT, endpoint, cause = e)
            }
    }
}

// ---- Bridge 数据模型 ----
@Serializable
data class HealthResponse(val status: String = "ok", val version: String? = null)

@Serializable
data class BridgeProviderInfo(val provider: String, val label: String, val supportsQuota: Boolean = true)

@Serializable
data class BridgeProvidersPayload(val providers: List<BridgeProviderInfo>)

@Serializable
data class BridgeBucket(
    val id: String,
    val name: String,
    val type: String = "PERCENT",
    val used: Double? = null,
    val limit: Double? = null,
    val remaining: Double? = null,
    val usedPercent: Double? = null,
    val remainingPercent: Double? = null,
    val unit: String? = null,
    val windowType: String = "CUSTOM",
    val windowStartAt: String? = null,
    val resetAt: String? = null
)

@Serializable
data class BridgeQuota(
    val provider: String,
    val accountId: String,
    val accountName: String? = null,
    val plan: String? = null,
    val buckets: List<BridgeBucket> = emptyList(),
    val balance: BridgeBalance? = null,
    val queriedAt: String? = null,
    val source: String = "bridge"
)

@Serializable
data class BridgeBalance(
    val available: Double,
    val currency: String? = null,
    val unit: String? = null
)

@Serializable
data class PairResult(val paired: Boolean = false, val accountId: String? = null)

/** 把 bridge ISO 时间转 Instant，解析失败返回 null */
fun parseIso(s: String?): Instant? {
    if (s.isNullOrBlank()) return null
    return runCatching {
        try {
            Instant.parse(s)
        } catch (e: Exception) {
            DateTimeFormatter.ISO_INSTANT.parse(s)
            Instant.parse(s)
        }
    }.getOrNull()
}