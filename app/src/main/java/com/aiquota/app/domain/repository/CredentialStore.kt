package com.aiquota.app.domain.repository

import com.aiquota.app.domain.model.ProviderCredential

/**
 * 统一凭据存取接口。
 *
 * 负责 CredentialEntity（Room）+ SecureCipherStore（Android Keystore AES-GCM）的编排：
 * - 保存时把 ProviderCredential 序列化后用 SecureCipherStore 加密落库；
 * - 读取时解密还原为 ProviderCredential；
 * - 删除时从 Room 移除密文。
 *
 * AccountRepository（保存侧）与 QuotaRepository（查询侧）统一经由本接口读写，
 * 禁止任何模块自行用奇怪的前缀读写 SecureCipherStore。
 */
interface CredentialStore {

    suspend fun save(accountId: String, credential: ProviderCredential)

    suspend fun load(accountId: String): ProviderCredential?

    suspend fun delete(accountId: String)
}