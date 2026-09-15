package com.aiquota.app.provider.bridge

import com.aiquota.app.core.network.NetworkFactory
import com.aiquota.app.core.network.toQueryError
import com.aiquota.app.domain.model.AuthResult
import com.aiquota.app.domain.model.Balance
import com.aiquota.app.domain.model.CredentialType
import com.aiquota.app.domain.model.DataSource
import com.aiquota.app.domain.model.ProviderCapabilities
import com.aiquota.app.domain.model.ProviderCredential
import com.aiquota.app.domain.model.ProviderExecutionContext
import com.aiquota.app.domain.model.QueryError
import com.aiquota.app.domain.model.QuotaBucket
import com.aiquota.app.domain.model.QuotaSnapshot
import com.aiquota.app.domain.model.QuotaType
import com.aiquota.app.domain.model.WindowType
import com.aiquota.app.domain.repository.QuotaProvider
import com.aiquota.app.provider.base.SnapshotBuilder
import okhttp3.OkHttpClient
import java.time.Instant
import java.util.UUID

/**
 * 通用 Bridge Provider。用于无法从 Android 直接调用官方 API，
 * 但可通过本地 CLI / Dashboard 查询的厂商（Codex / OpenCode / GLM / MiniMax）。
 * bridge url + secret 由上层通过 [ProviderExecutionContext.credential] 解密注入，
 * 本 Provider 不自行访问任何 Repository/SecureCipherStore。
 */
class BridgeQuotaProvider(
    override val providerId: String,
    private val httpClient: OkHttpClient = NetworkFactory.buildHttpClient()
) : QuotaProvider {

    /**
     * Test Connection（P0-2）：不只是验证 Bridge 在线，而是逐级验证到目标 Provider。
     *
     * 1) GET /health           -> Bridge 在线
     * 2) POST /pair            -> Bridge Secret 是否正确
     * 3) GET /providers        -> 目标 Provider 是否注册、supportsQuota
     * 4) GET /quota (probe)    -> 轻量额度探测，验证 Adapter 真的可返回有效额度
     *
     * 直到目标 Provider 真正通过检查，才会返回 Success；否则返回带中文细因的 Failure
     * （区分：Bridge 在线但未配置 Token / 不支持 / 数据异常 / Secret 错误 / 不可达）。
     */
    override suspend fun authenticate(credential: ProviderCredential): AuthResult {
        val conn = bridgeConnection(credential)
        if (conn == null) return AuthResult.Failure(QueryError.BridgeOffline, "请先填写 Bridge 地址")
        val client = BridgeClient(conn.url, httpClient)
        val secret = conn.secret ?: ""
        return try {
            client.health()
            // 2) secret
            try {
                client.pair(secret)
            } catch (e: BridgeProtocolException) {
                val qe = mapBridgeError(e)
                return if (qe is QueryError.Unauthorized || qe is QueryError.Forbidden) {
                    AuthResult.Failure(QueryError.Unauthorized(), "Bridge Secret 错误")
                } else {
                    AuthResult.Failure(qe, "Bridge 配对失败")
                }
            }
            // 3) 目标 Provider 能力
            val info = client.providers().firstOrNull { it.provider == providerId }
                ?: return AuthResult.Failure(
                    QueryError.ProviderUnavailable,
                    "Bridge 在线，但未注册 $providerId Provider"
                )
            if (!info.supportsQuota) {
                return AuthResult.Failure(
                    QueryError.ProviderUnavailable,
                    "Bridge 在线 · ${info.label} 未就绪（未配置密钥，暂无额度数据）"
                )
            }
            // 4) 轻量额度探测
            val probe = try {
                client.quota(secret, providerId, accountId = "probe")
            } catch (e: BridgeProtocolException) {
                return AuthResult.Failure(mapBridgeError(e), "额度探测失败（${info.label}）")
            }
            return when (val err = BridgeQuotaValidator.validate(probe)) {
                is QueryError.ProviderUnavailable ->
                    AuthResult.Failure(err, "Bridge 在线 · ${info.label} 未配置 Token/密钥")
                null ->
                    AuthResult.Success("Bridge 连接成功 · ${info.label} 可用 · 额度接口响应正常")
                else ->
                    AuthResult.Failure(err, "Bridge 在线 · ${info.label} 响应异常：${err.userMessage()}")
            }
        } catch (e: BridgeProtocolException) {
            AuthResult.Failure(mapBridgeError(e))
        } catch (e: QueryError) {
            AuthResult.Failure(e)
        } catch (e: Exception) {
            AuthResult.Failure(e.toQueryError())
        }
    }

    override suspend fun fetchQuota(context: ProviderExecutionContext): QuotaSnapshot {
        val account = context.account
        val conn = context.credential?.let { bridgeConnection(it) } ?: throw QueryError.BridgeOffline
        val client = BridgeClient(conn.url, httpClient)
        val quota = try {
            client.quota(conn.secret ?: "", provider = providerId, accountId = account.id)
        } catch (e: com.aiquota.app.provider.bridge.BridgeProtocolException) {
            throw mapBridgeError(e)
        } catch (e: QueryError) {
            throw e
        } catch (e: Exception) {
            throw e.toQueryError()
        }
        // P0-3 / P0-9：强校验 —— unsupported / 空 buckets / 越界 / 非法 windowType 一律拒绝，
        // 绝不生成空 Snapshot 后标记 LIVE。
        BridgeQuotaValidator.validate(quota)?.let { throw it }
        val buckets = quota.buckets.map { b ->
            QuotaBucket(
                id = b.id,
                name = b.name,
                type = parseQuotaType(b.type),
                used = b.used,
                limit = b.limit,
                remaining = b.remaining,
                // 仅做极小的边界归一（-0.00001 -> 0），明显的越界已在 Validator 拒绝
                usedPercent = b.usedPercent?.coerceIn(0.0, 100.0),
                remainingPercent = b.remainingPercent?.coerceIn(0.0, 100.0),
                unit = b.unit,
                windowType = parseWindowType(b.windowType),
                windowStartAt = parseIso(b.windowStartAt),
                resetAt = parseIso(b.resetAt)
            )
        }
        val balance = quota.balance?.let {
            Balance(
                id = UUID.randomUUID().toString(),
                name = "余额",
                available = it.available,
                currency = it.currency,
                unit = it.unit,
                updatedAt = Instant.now()
            )
        }
        return SnapshotBuilder.build(
            providerId = providerId,
            accountId = account.id,
            accountName = account.displayName,
            planName = quota.plan,
            buckets = buckets,
            balance = balance,
            source = DataSource.BRIDGE,
            queriedAt = parseIso(quota.queriedAt) ?: Instant.now()
        )
    }

    override suspend fun validateCredential(credential: ProviderCredential): Boolean =
        authenticate(credential) is AuthResult.Success

    override fun capabilities(): ProviderCapabilities = ProviderCapabilities(
        supportsDirectApi = false,
        supportsBridge = true,
        credentialTypes = listOf(CredentialType.BRIDGE)
    )

    /**
     * 把 Bridge 协议层结构化异常映射为明确的三方 / 服务端错误（P0-11）。
     * 不再依赖正则解析 message，直接读取 [BridgeProtocolException.statusCode]。
     * 例：401 -> Unauthorized，429 -> RateLimited，5xx -> ServerError，超时 -> Timeout，
     * 连接失败 -> BridgeOffline。
     */
    private fun mapBridgeError(e: BridgeProtocolException): QueryError = when (e.statusCode) {
        401 -> QueryError.Unauthorized("Bridge 返回 401")
        403 -> QueryError.Forbidden()
        404 -> QueryError.ProviderUnavailable
        429 -> QueryError.RateLimited()
        in 500..599 -> QueryError.ServerError(e.statusCode)
        BridgeProtocolException.TIMEOUT -> QueryError.Timeout
        BridgeProtocolException.CONNECT -> QueryError.BridgeOffline
        else -> QueryError.BridgeOffline
    }

    private fun bridgeConnection(c: ProviderCredential): BridgeConnection? {
        val url = c.extra["bridgeUrl"]
            .takeIf { !it.isNullOrBlank() }
            ?: c.secret?.takeIf { it.startsWith("http") }
        val secret = c.extra["bridgeSecret"] ?: c.extra["secret"]
        return if (url == null) null else BridgeConnection(url, secret)
    }

    private fun parseWindowType(name: String): WindowType =
        runCatching { WindowType.valueOf(name) }.getOrDefault(WindowType.CUSTOM)

    private fun parseQuotaType(name: String): QuotaType =
        runCatching { QuotaType.valueOf(name) }.getOrDefault(QuotaType.PERCENT)
}

data class BridgeConnection(val url: String, val secret: String?)