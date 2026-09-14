package com.aiquota.app.data.repository

import com.aiquota.app.core.security.SecureCipherStore
import com.aiquota.app.data.database.dao.AccountDao
import com.aiquota.app.data.database.dao.QuotaDao
import com.aiquota.app.data.database.entity.CredentialEntity
import com.aiquota.app.data.mapper.Mapper
import com.aiquota.app.domain.model.AuthResult
import com.aiquota.app.domain.model.ProviderAccount
import com.aiquota.app.domain.model.ProviderCredential
import com.aiquota.app.domain.model.ProviderId
import com.aiquota.app.domain.repository.AccountRepository
import com.aiquota.app.provider.ProviderRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/** 账号仓储：账户元数据 + 加密后的凭据（安全存储）。 */
class AccountRepositoryImpl @Inject constructor(
    private val accountDao: AccountDao,
    private val quotaDao: QuotaDao,
    private val secureCipherStore: SecureCipherStore,
    private val providerRegistry: ProviderRegistry
) : AccountRepository {

    override fun observeAccounts(): Flow<List<ProviderAccount>> =
        accountDao.observeAll().map { list -> list.map { Mapper.toAccount(it) } }

    override suspend fun getAllAccounts(): List<ProviderAccount> =
        accountDao.getAll().map { Mapper.toAccount(it) }

    override suspend fun getAccount(id: String): ProviderAccount? =
        accountDao.getById(id)?.let { Mapper.toAccount(it) }

    override suspend fun addAccount(
        providerId: String,
        displayName: String,
        connectorType: ProviderAccount.ConnectorType
    ): ProviderAccount {
        val now = Instant.now()
        val account = ProviderAccount(
            id = UUID.randomUUID().toString(),
            providerId = providerId,
            displayName = displayName,
            connectorType = connectorType,
            enabled = true,
            createdAt = now,
            updatedAt = now
        )
        accountDao.upsert(Mapper.toEntity(account))
        return account
    }

    override suspend fun updateAccount(account: ProviderAccount) =
        accountDao.upsert(Mapper.toEntity(account.copy(updatedAt = Instant.now())))

    override suspend fun setEnabled(id: String, enabled: Boolean) =
        accountDao.setEnabled(id, enabled, Instant.now().toEpochMilli())

    override suspend fun deleteAccount(id: String) {
        accountDao.getById(id)?.let { accountDao.delete(it) }
        quotaDao.deleteCredential(id)
        quotaDao.deleteSnapshot(id)
        quotaDao.deleteBuckets(id)
    }

    override suspend fun attachCredential(accountId: String, credential: ProviderCredential) {
        val extra = credential.extra.filterValues { it.isNotBlank() }
        val blobText = buildString {
            append(credential.secret?.trim().orEmpty())
            append('\n')
            append(extra.entries.joinToString("\n") { "${it.key}=${it.value}" })
        }
        val encrypted = secureCipherStore.encrypt(blobText, accountId)
        quotaDao.upsertCredential(
            CredentialEntity(
                ref = "cred:$accountId",
                accountId = accountId,
                credentialType = credential.type.name,
                encryptedBlob = encrypted
            )
        )
    }

    override suspend fun readCredential(accountId: String): ProviderCredential? {
        val entity = quotaDao.getCredential(accountId) ?: return null
        val plain = secureCipherStore.decrypt(entity.encryptedBlob, accountId) ?: return null
        val lines = plain.split('\n').map { it.trim() }
        val secret = lines.firstOrNull()?.takeIf { it.isNotBlank() }
        val extra = lines.drop(1)
            .mapNotNull { l ->
                val i = l.indexOf('=')
                if (i <= 0) null else l.substring(0, i) to l.substring(i + 1)
            }
            .toMap()
        val account = getAccount(accountId)
        return ProviderCredential(
            providerId = account?.providerId.orEmpty(),
            type = runCatching { com.aiquota.app.domain.model.CredentialType.valueOf(entity.credentialType) }
                .getOrDefault(com.aiquota.app.domain.model.CredentialType.API_KEY),
            secret = secret,
            extra = extra
        )
    }

    override suspend fun testConnection(account: ProviderAccount): AuthResult {
        val provider = providerRegistry.getProvider(account.providerId)
            ?: return AuthResult.Failure(com.aiquota.app.domain.model.QueryError.Unknown("未注册平台"))
        val credential = readCredential(account.id)
            ?: ProviderCredential(
                providerId = account.providerId,
                type = com.aiquota.app.domain.model.CredentialType.BRIDGE,
                extra = emptyMap()
            )
        return provider.authenticate(credential)
    }
}