package com.aiquota.app.domain.repository

import com.aiquota.app.domain.model.AuthResult
import com.aiquota.app.domain.model.ProviderCapabilities
import com.aiquota.app.domain.model.ProviderCredential
import com.aiquota.app.domain.model.ProviderExecutionContext
import com.aiquota.app.domain.model.QuotaSnapshot

/**
 * 统一 Provider 接口。主程序禁止直接依赖任何平台私有 API 数据结构，
 * QuotaProvider 负责把平台响应归一化为 QuotaSnapshot。
 */
interface QuotaProvider {

    val providerId: String

    suspend fun authenticate(credential: ProviderCredential): AuthResult

    suspend fun fetchQuota(context: ProviderExecutionContext): QuotaSnapshot

    suspend fun validateCredential(credential: ProviderCredential): Boolean

    fun capabilities(): ProviderCapabilities
}