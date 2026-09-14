package com.aiquota.app.provider

import com.aiquota.app.domain.model.ProviderId
import com.aiquota.app.domain.repository.QuotaProvider

/**
 * Provider 注册中心。
 *
 * 统一登记并路由各平台的 QuotaProvider，主程序/Repository 不直接依赖任何某个
 * 平台的私有实现，只通过 ProviderId 从注册中心取实例。新增平台只需两步：
 * 1) 在 [ProviderId] 追加枚举；
 * 2) 在 DI 模块构建实例并放入本注册中心。
 */
class ProviderRegistry(
    private val providers: Map<ProviderId, QuotaProvider>
) {

    private val byKey: Map<String, QuotaProvider> =
        providers.mapKeys { it.key.key }

    /** 按枚举取 Provider；未注册返回 null。 */
    fun getProvider(providerId: ProviderId): QuotaProvider? = providers[providerId]

    /** 按平台字符串 key（如 "glm"）取 Provider；未知返回 null。 */
    fun getProvider(key: String): QuotaProvider? = byKey[key]

    /** 解析任意 key（或枚举 key）到 ProviderId。 */
    fun resolveId(key: String): ProviderId? =
        ProviderId.fromKey(key) ?: providers.keys.firstOrNull { it.key == key }

    /** 全部已注册 Provider（顺序稳定），用于遍历刷新。 */
    fun all(): List<QuotaProvider> = providers.values.toList()

    /** 已注册的平台集合。 */
    fun registeredIds(): Set<ProviderId> = providers.keys

    /** 是否已注册该平台。 */
    fun contains(providerId: ProviderId): Boolean = providers.containsKey(providerId)
}