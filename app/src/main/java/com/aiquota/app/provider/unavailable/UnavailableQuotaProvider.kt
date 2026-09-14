package com.aiquota.app.provider.unavailable

import com.aiquota.app.domain.model.AuthResult
import com.aiquota.app.domain.model.CredentialType
import com.aiquota.app.domain.model.ProviderAccount
import com.aiquota.app.domain.model.ProviderCapabilities
import com.aiquota.app.domain.model.ProviderCredential
import com.aiquota.app.domain.model.QueryError
import com.aiquota.app.domain.model.QuotaSnapshot
import com.aiquota.app.domain.repository.QuotaProvider

/**
 * 平台未开放可查询接口时的 Provider（如 Grok 个人套餐）。
 * 不伪造额度，一律返回 ProviderUnavailable，UI 显示"平台暂未开放可用额度接口"。
 */
class UnavailableQuotaProvider(
    override val providerId: String
) : QuotaProvider {

    override suspend fun authenticate(credential: ProviderCredential): AuthResult =
        AuthResult.Failure(QueryError.ProviderUnavailable)

    override suspend fun fetchQuota(account: ProviderAccount): QuotaSnapshot =
        throw QueryError.ProviderUnavailable

    override suspend fun validateCredential(credential: ProviderCredential): Boolean = false

    override fun capabilities(): ProviderCapabilities = ProviderCapabilities(
        supportsDirectApi = false,
        supportsBridge = false,
        credentialTypes = emptyList()
    )
}