package com.aiquota.app.provider.debug

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
import java.io.IOException
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Debug Provider：仅存在于 debug 构建，用于无真实账号时验证 UI 与状态。
 * 支持模拟 100% / 61% / 20% / 5% / 0% 以及 LIVE / CACHED / NETWORK_ERROR / 401 / 429 / 5xx。
 * Release 构建完全不包含此文件（位于 src/debug）。
 */
enum class MockScenario(val key: String) {
    P100("100"),
    P61("61"),
    P20("20"),
    P5("5"),
    P0("0"),
    NETWORK_ERROR("network_error"),
    UNAUTHORIZED("401"),
    RATE_LIMITED("429"),
    SERVER_ERROR("5xx");

    companion object {
        fun from(v: String?): MockScenario =
            entries.firstOrNull { it.key.equals(v, ignoreCase = true) } ?: P61
    }
}

class DebugQuotaProvider : QuotaProvider {

    override val providerId: String = "debug"

    override suspend fun authenticate(credential: ProviderCredential): AuthResult =
        AuthResult.Success("Debug 已连接")

    override suspend fun fetchQuota(context: ProviderExecutionContext): QuotaSnapshot {
        val account = context.account
        val scenario = MockScenario.from(accountCredentialScenario[account.id] ?: "61")
        when (scenario) {
            MockScenario.NETWORK_ERROR -> throw IOException("simulated network").toQueryError()
            MockScenario.UNAUTHORIZED -> throw QueryError.Unauthorized("debug-401")
            MockScenario.RATE_LIMITED -> throw QueryError.RateLimited(300)
            MockScenario.SERVER_ERROR -> throw QueryError.ServerError(502)
            else -> {} // 正常返回模拟额度
        }
        val r = scenario.remainingPercent()
        val now = Instant.now()
        return SnapshotBuilder.build(
            providerId = providerId,
            accountId = account.id,
            accountName = "Debug 账号",
            planName = "Debug Plan",
            buckets = listOf(
                QuotaBucket(
                    id = "${account.id}:5h",
                    name = "5小时额度",
                    type = QuotaType.PERCENT,
                    remainingPercent = r,
                    usedPercent = (100.0 - r).coerceIn(0.0, 100.0),
                    windowType = WindowType.ROLLING_5_HOURS,
                    resetAt = now.plus(3, ChronoUnit.HOURS)
                ),
                QuotaBucket(
                    id = "${account.id}:week",
                    name = "每周额度",
                    type = QuotaType.PERCENT,
                    remainingPercent = r,
                    usedPercent = (100.0 - r).coerceIn(0.0, 100.0),
                    windowType = WindowType.WEEKLY,
                    resetAt = now.plus(2, ChronoUnit.DAYS)
                )
            ),
            balance = Balance(
                id = "${account.id}:bal",
                name = "余额",
                available = r,
                currency = "USD",
                unit = "$",
                updatedAt = now
            ),
            source = DataSource.MOCK,
            queriedAt = now
        )
    }

    override suspend fun validateCredential(credential: ProviderCredential): Boolean = true

    override fun capabilities(): ProviderCapabilities = ProviderCapabilities(
        supportsDirectApi = true,
        supportsBridge = false,
        credentialTypes = listOf(CredentialType.MOCK)
    )

    /** 调度层注入当前 debug 账号要模拟的场景 */
    var accountCredentialScenario: MutableMap<String, String> = mutableMapOf()
    fun configure(accountId: String, scenario: String) {
        accountCredentialScenario[accountId] = scenario
    }

    private fun MockScenario.remainingPercent(): Double = when (this) {
        MockScenario.P100 -> 100.0
        MockScenario.P61 -> 61.0
        MockScenario.P20 -> 20.0
        MockScenario.P5 -> 5.0
        MockScenario.P0 -> 0.0
        else -> 61.0
    }
}