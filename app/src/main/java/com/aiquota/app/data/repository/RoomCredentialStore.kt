package com.aiquota.app.data.repository

import com.aiquota.app.core.security.SecureCipherStore
import com.aiquota.app.data.database.dao.QuotaDao
import com.aiquota.app.data.database.entity.CredentialEntity
import com.aiquota.app.domain.model.CredentialType
import com.aiquota.app.domain.model.ProviderCredential
import com.aiquota.app.domain.repository.CredentialStore
import javax.inject.Inject

/**
 * 基于 Room（CredentialEntity）+ SecureCipherStore 的凭据存取实现。
 *
 * - save：ProviderCredential → 文本序列化 → AES-GCM 加密（objectName=accountId）→ 落库；
 * - load：取回密文 → 使用相同 AAD 解密 → 反序列化 ProviderCredential；
 * - delete：移除密文。
 *
 * 提供统一的、账号绑定的密文读写，密文无法跨账号解密。
 */
class RoomCredentialStore @Inject constructor(
    private val quotaDao: QuotaDao,
    private val secureCipherStore: SecureCipherStore
) : CredentialStore {

    override suspend fun save(accountId: String, credential: ProviderCredential) {
        val plain = serialize(credential)
        val encrypted = secureCipherStore.encrypt(plain, accountId)
        quotaDao.upsertCredential(
            CredentialEntity(
                ref = "cred:$accountId",
                accountId = accountId,
                credentialType = credential.type.name,
                encryptedBlob = encrypted
            )
        )
    }

    override suspend fun load(accountId: String): ProviderCredential? {
        val entity = quotaDao.getCredential(accountId) ?: return null
        val plain = secureCipherStore.decrypt(entity.encryptedBlob, accountId) ?: return null
        return deserialize(plain, entity.credentialType)
    }

    override suspend fun delete(accountId: String) {
        quotaDao.deleteCredential(accountId)
    }

    // ---- 序列化： 首行版本标记，随后 secret / type / providerId / extra k=v ----
    private fun serialize(c: ProviderCredential): String = buildString {
        appendLine(VERSION)
        appendLine(c.secret.orEmpty())
        appendLine(c.type.name)
        appendLine(c.providerId)
        c.extra.forEach { (k, v) -> appendLine("$k=$v") }
    }

    private fun deserialize(plain: String, persistedType: String): ProviderCredential? {
        val lines = plain.split('\n')
        if (lines.firstOrNull() != VERSION) return null
        val secret = lines.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
        val typeName = lines.getOrNull(2)?.trim().takeIf { !it.isNullOrBlank() } ?: persistedType
        val providerId = lines.getOrNull(3)?.trim().orEmpty()
        val extra = lines.drop(4)
            .mapNotNull { l ->
                val i = l.indexOf('=')
                if (i <= 0) null else l.substring(0, i) to l.substring(i + 1)
            }
            .filter { it.second.isNotBlank() }
            .toMap()
        val type = runCatching { CredentialType.valueOf(typeName) }.getOrDefault(CredentialType.API_KEY)
        return ProviderCredential(
            providerId = providerId,
            type = type,
            secret = secret,
            extra = extra
        )
    }

    private companion object {
        const val VERSION = "#cred-v1"
    }
}