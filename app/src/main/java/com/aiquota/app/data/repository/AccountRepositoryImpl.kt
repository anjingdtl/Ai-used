package com.aiquota.app.data.repository

import com.aiquota.app.data.database.dao.AccountDao
import com.aiquota.app.data.database.dao.QuotaDao
import com.aiquota.app.data.mapper.Mapper
import com.aiquota.app.domain.model.AuthResult
import com.aiquota.app.domain.model.CredentialType
import com.aiquota.app.domain.model.ProviderAccount
import com.aiquota.app.domain.model.ProviderCredential
import com.aiquota.app.domain.model.QueryError
import com.aiquota.app.domain.repository.AccountRepository
import com.aiquota.app.domain.repository.CredentialStore
import com.aiquota.app.provider.ProviderRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/** 账号仓储：账户元数据 + 凭据统一经由 CredentialStore（安全存储）。 */
class AccountRepositoryImpl @Inject constructor(
    private val accountDao: AccountDao,
    private val quotaDao: QuotaDao,
    private val credentialStore: CredentialStore,
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
        credentialStore.delete(id)
        quotaDao.deleteSnapshot(id)
        quotaDao.deleteBuckets(id)
    }

    override suspend fun attachCredential(accountId: String, credential: ProviderCredential) {
        credentialStore.save(accountId, credential)
    }

    override suspend fun readCredential(accountId: String): ProviderCredential? =
        credentialStore.load(accountId)

    override suspend fun testConnection(account: ProviderAccount): AuthResult {
        val provider = providerRegistry.getProvider(account.providerId)
            ?: return AuthResult.Failure(QueryError.Unknown("未注册平台"))
        val credential = readCredential(account.id)
            ?: ProviderCredential(
                providerId = account.providerId,
                type = CredentialType.BRIDGE,
                extra = emptyMap()
            )
        return provider.authenticate(credential)
    }

    override suspend fun testCredential(providerId: String, credential: ProviderCredential): AuthResult {
        val provider = providerRegistry.getProvider(providerId)
            ?: return AuthResult.Failure(QueryError.Unknown("未注册平台"))
        return provider.authenticate(credential)
    }
}