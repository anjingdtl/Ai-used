package com.aiquota.app.di

import com.aiquota.app.core.network.NetworkFactory
import com.aiquota.app.core.security.SecureCipherStore
import com.aiquota.app.domain.model.ProviderId
import com.aiquota.app.domain.repository.QuotaProvider
import com.aiquota.app.provider.ProviderRegistry
import com.aiquota.app.provider.bridge.BridgeConnection
import com.aiquota.app.provider.bridge.BridgeQuotaProvider
import com.aiquota.app.provider.debug.DebugQuotaProvider
import com.aiquota.app.provider.unavailable.UnavailableQuotaProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provider DI。Bridge 平台的 url + secret 全部通过 SecureCipherStore 按账号加密读取，
 * 不写死、不落明文；未配置桥接的账号返回 null -> BridgeOffline。
 */
@Module
@InstallIn(SingletonComponent::class)
object ProviderModule {

    private const val BRIDGE_PREFIX = "bridge:" // 加密对象命名空间

    @Provides
    @Singleton
    fun provideSecureCipherStore(): SecureCipherStore = SecureCipherStore()

    @Provides
    @Singleton
    fun provideBridgeLoader(
        secureCipherStore: SecureCipherStore
    ): suspend (accountId: String) -> BridgeConnection? = { accountId ->
        val blob = secureCipherStore.decrypt(BRIDGE_PREFIX + accountId, BRIDGE_PREFIX)
        // blob = "<url>\n<secret>"
        blob?.split("\n", limit = 2)
            ?.map { it.trim() }
            ?.let { parts ->
                val url = parts.firstOrNull()?.takeIf { it.isNotBlank() } ?: return@let null
                BridgeConnection(url = url, secret = parts.getOrNull(1)?.takeIf { it.isNotBlank() })
            }
    }

    @Provides
    @Singleton
    fun provideProviderRegistry(
        secretLoader: suspend (accountId: String) -> BridgeConnection?
    ): ProviderRegistry {
        val codex = BridgeQuotaProvider(
            providerId = ProviderId.OPENAI_CODEX.key,
            secretLoader = secretLoader,
            httpClient = NetworkFactory.buildHttpClient()
        )
        val glm = BridgeQuotaProvider(
            providerId = ProviderId.GLM.key,
            secretLoader = secretLoader,
            httpClient = NetworkFactory.buildHttpClient()
        )
        val minimax = BridgeQuotaProvider(
            providerId = ProviderId.MINIMAX.key,
            secretLoader = secretLoader,
            httpClient = NetworkFactory.buildHttpClient()
        )
        val opencode = BridgeQuotaProvider(
            providerId = ProviderId.OPENCODE_GO.key,
            secretLoader = secretLoader,
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