package com.aiquota.app.di

import com.aiquota.app.core.network.NetworkFactory
import com.aiquota.app.core.security.SecureCipherStore
import com.aiquota.app.data.database.dao.QuotaDao
import com.aiquota.app.data.repository.RoomCredentialStore
import com.aiquota.app.domain.model.ProviderId
import com.aiquota.app.domain.repository.CredentialStore
import com.aiquota.app.domain.repository.QuotaProvider
import com.aiquota.app.provider.ProviderRegistry
import com.aiquota.app.provider.bridge.BridgeQuotaProvider
import com.aiquota.app.provider.debug.DebugQuotaProvider
import com.aiquota.app.provider.unavailable.UnavailableQuotaProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provider DI。
 *
 * 凭据统一由 [CredentialStore]（CredentialEntity + SecureCipherStore）保存/读取，
 * 并在查询时由 QuotaRepository 解密后通过 ProviderExecutionContext 注入 Provider。
 * 本模块禁止自行用任何前缀生成 key 去读 SecureCipherStore。
 *
 * Provider 只负责拿到账号 + 凭据后执行真实查询，不反向依赖 Repository。
 */
@Module
@InstallIn(SingletonComponent::class)
object ProviderModule {

    @Provides
    @Singleton
    fun provideSecureCipherStore(): SecureCipherStore = SecureCipherStore()

    @Provides
    @Singleton
    fun provideCredentialStore(
        quotaDao: QuotaDao,
        secureCipherStore: SecureCipherStore
    ): CredentialStore = RoomCredentialStore(quotaDao, secureCipherStore)

    @Provides
    @Singleton
    fun provideProviderRegistry(): ProviderRegistry {
        val codex = BridgeQuotaProvider(
            providerId = ProviderId.OPENAI_CODEX.key,
            httpClient = NetworkFactory.buildHttpClient()
        )
        val glm = BridgeQuotaProvider(
            providerId = ProviderId.GLM.key,
            httpClient = NetworkFactory.buildHttpClient()
        )
        val minimax = BridgeQuotaProvider(
            providerId = ProviderId.MINIMAX.key,
            httpClient = NetworkFactory.buildHttpClient()
        )
        val opencode = BridgeQuotaProvider(
            providerId = ProviderId.OPENCODE_GO.key,
            httpClient = NetworkFactory.buildHttpClient()
        )
        val grok = UnavailableQuotaProvider(providerId = ProviderId.GROK.key)
        val debug = DebugQuotaProvider()

        val map: Map<ProviderId, QuotaProvider> = linkedMapOf(
            ProviderId.GLM to glm,
            ProviderId.OPENAI_CODEX to codex,
            ProviderId.MINIMAX to minimax,
            ProviderId.OPENCODE_GO to opencode,
            ProviderId.GROK to grok,
            ProviderId.DEBUG to debug
        )
        return ProviderRegistry(map)
    }
}