package com.aiquota.app.domain.model

/** 凭据种类，避免将 Coding Plan Key 与普通 API Key 混用 */
enum class CredentialType {
    API_KEY,        // GLM Coding Plan Key / 普通 API Key
    XAI_API_KEY,    // xAI API key
    BRIDGE,         // Bridge 配对
    MOCK            // 仅 debug
}

/**
 * Provider 凭据。真实 Secret 永远不会出现在这里用于持久化，
 * 仅用于在内存中传递并即时写入安全存储。
 */
data class ProviderCredential(
    val providerId: String,
    val type: CredentialType,
    val secret: String? = null,
    val extra: Map<String, String> = emptyMap()
)

/** 鉴权结果 */
sealed class AuthResult {
    data class Success(val planName: String? = null) : AuthResult()
    /** [detail] 为可选的、面向用户的中文细因，比 [error.userMessage] 更精确（如“Bridge 在线 · GLM Adapter 未配置 Token”） */
    data class Failure(val error: QueryError, val detail: String? = null) : AuthResult()
}

/** 平台能力声明 */
data class ProviderCapabilities(
    val supportsDirectApi: Boolean,
    val supportsBridge: Boolean,
    val credentialTypes: List<CredentialType>
)