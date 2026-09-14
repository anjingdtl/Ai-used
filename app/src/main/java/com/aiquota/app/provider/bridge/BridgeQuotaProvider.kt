package com.aiquota.app.provider.bridge

import com.aiquota.app.core.network.NetworkFactory
import com.aiquota.app.core.network.toQueryError
import com.aiquota.app.domain.model.AuthResult
import com.aiquota.app.domain.model.Balance
import com.aiquota.app.domain.model.CredentialType
import com.aiquota.app.domain.model.DataSource
import com.aiquota.app.domain.model.ProviderAccount
import com.aiquota.app.domain.model.ProviderCapabilities
import com.aiquota.app.domain.model.ProviderCredential
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
 * bridge url + secret 由安全存储按账号注入（不写死、不落明文）。
 */
class BridgeQuotaProvider(
    override val providerId: String,
    private val secretLoader: suspend (accountId: String) -> BridgeConnection?,
    private val httpClient: OkHttpClient = NetworkFactory.buildHttpClient()
) : QuotaProvider {

    override suspend fun authenticate(credential: ProviderCredential): AuthResult {
        val conn = bridgeConnection(credential)
        if (conn == null) return AuthResult.Failure(QueryError.BridgeOffline)
        return try {
            val client = BridgeClient(conn.url, httpClient)
            client.health()
            client.pair(conn.secret ?: "")
            AuthResult.Success("Bridge 已连接")
        } catch (e: Exception) {
            AuthResult.Failure(e.toQueryError())
        }
    }

    override suspend fun fetchQuota(account: ProviderAccount): QuotaSnapshot {
        val conn = secretLoader(account.id) ?: throw QueryError.BridgeOffline
        val client = BridgeClient(conn.url, httpClient)
        val quota = try {
            client.quota(conn.secret ?: "", provider = providerId, accountId = account.id)
        } catch (e: com.aiquota.app.provider.bridge.BridgeProtocolException) {
            throw QueryError.BridgeOffline
        } catch (e: QueryError) {
            throw e
        } catch (e: Exception) {
            throw e.toQueryError()
        }
        val buckets = quota.buckets.map { b ->
            QuotaBucket(
                id = b.id,
                name = b.name,
                type = parseQuotaType(b.type),
                used = b.used,
                limit = b.limit,
                remaining = b.remaining,
                usedPercent = b.usedPercent,
                remainingPercent = b.remainingPercent,
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