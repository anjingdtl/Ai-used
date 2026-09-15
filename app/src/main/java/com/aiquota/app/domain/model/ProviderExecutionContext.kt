package com.aiquota.app.domain.model

/**
 * 一次额度查询所需的完整上下文。
 *
 * Provider 只依赖本上下文完成查询，不反向依赖 Repository/Dao，
 * 从而避免 Provider → Repository → Provider 的循环依赖。
 * Credential 由上层（QuotaRepository）通过 CredentialStore 解密后注入。
 */
data class ProviderExecutionContext(
    val account: ProviderAccount,
    val credential: ProviderCredential?
)