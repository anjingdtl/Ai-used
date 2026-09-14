package com.aiquota.app.provider.bridge

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Quota Bridge 局域网协议客户端。
 * 协议：GET /health、GET /providers、GET /quota、POST /pair
 */
class BridgeClient(
    private val baseUrl: String,
    private val client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private fun url(path: String) = baseUrl.trimEnd('/') + path

    @Throws(Exception::class)
    suspend fun health(): HealthResponse {
        val resp = newCall("GET", "/health")
        if (resp.code != 200) throw BridgeProtocolException("health=${resp.code}")
        return json.decodeFromString(HealthResponse.serializer(), resp.body!!.string())
    }

    @Throws(Exception::class)
    suspend fun providers(): List<BridgeProviderInfo> {
        val resp = newCall("GET", "/providers")
        if (resp.code != 200) throw BridgeProtocolException("providers=${resp.code}")
        return json.decodeFromString(BridgeProvidersPayload.serializer(), resp.body!!.string()).providers
    }

    @Throws(Exception::class)
    suspend fun quota(secret: String, provider: String?, accountId: String?): BridgeQuota {
        val request = Request.Builder()
            .url(url("/quota"))
            .addHeader("Authorization", "Bearer $secret")
            .addHeader("X-Provider", provider ?: "")
            .addHeader("X-Account", accountId ?: "")
            .build()
        val resp = execute(request)
        if (resp.code != 200) throw BridgeProtocolException("quota=${resp.code}")
        val raw = resp.body!!.string()
        return json.decodeFromString(BridgeQuota.serializer(), raw)
    }

    @Throws(Exception::class)
    suspend fun pair(secret: String): PairResult {
        val request = Request.Builder().url(url("/pair")).addHeader("Authorization", "Bearer $secret").build()
        val resp = execute(request)
        if (resp.code != 200) throw BridgeProtocolException("pair=${resp.code}")
        return json.decodeFromString(PairResult.serializer(), resp.body!!.string())
    }

    private suspend fun newCall(method: String, path: String): okhttp3.Response {
        val builder = Request.Builder().url(url(path))
        if (method == "GET") builder.get()
        return execute(builder.build())
    }

    private fun execute(req: Request): okhttp3.Response {
        val resp = client.newCall(req).execute()
        return resp
    }
}

class BridgeProtocolException(message: String) : Exception(message)

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