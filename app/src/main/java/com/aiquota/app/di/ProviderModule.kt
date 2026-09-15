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
import com.aiquota.app.provider.unavailable.UnavailableQuotaProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/**
 * Provider DI。
 *
 * 凭据统一由 [CredentialStore]（CredentialEntity + SecureCipherStore）保存/读取，
 * 并在查询时由 QuotaRepository 解密后通过 ProviderExecutionContext 注入 Provider。
 * 本模块禁止自行用任何前缀生成 key 去读 SecureCipherStore。
 *
 * Provider 注册采用 Set 多绑定：main 只登记真实平台；debug 专属 Provider
 * （DebugQuotaProvider / MockScenario）由 `src/debug` 的 DebugProviderModule 额外贡献，
 * Release 构建不编译也不注册任何 mock/debug 代码。
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
    fun provideProviderRegistry(
        providers: @JvmSuppressWildcards Set<@JvmSuppressWildcards QuotaProvider>
    ): ProviderRegistry {
        val map = linkedMapOf<ProviderId, QuotaProvider>()
        providers.forEach { p ->
            val id = ProviderId.fromKey(p.providerId)
            if (id != null) {
                map[id] = p
            }
        }
        return ProviderRegistry(map)
    }

    // ---- 真实平台 Provider（main 源集，Release 同样具备）----
    @Provides
    @IntoSet
    fun bindCodex(): QuotaProvider = BridgeQuotaProvider(
        providerId = ProviderId.OPENAI_CODEX.key,
        httpClient = NetworkFactory.buildHttpClient()
    )

    @Provides
    @IntoSet
    fun bindGlm(): QuotaProvider = BridgeQuotaProvider(
        providerId = ProviderId.GLM.key,
        httpClient = NetworkFactory.buildHttpClient()
    )

    @Provides
    @IntoSet
    fun bindMiniMax(): QuotaProvider = BridgeQuotaProvider(
        providerId = ProviderId.MINIMAX.key,
        httpClient = NetworkFactory.buildHttpClient()
    )

    @Provides
    @IntoSet
    fun bindOpenCode(): QuotaProvider = BridgeQuotaProvider(
        providerId = ProviderId.OPENCODE_GO.key,
        httpClient = NetworkFactory.buildHttpClient()
    )

    @Provides
    @IntoSet
    fun bindGrok(): QuotaProvider = UnavailableQuotaProvider(providerId = ProviderId.GROK.key)
}