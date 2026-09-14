package com.aiquota.app.domain.repository

import com.aiquota.app.domain.model.AuthResult
import com.aiquota.app.domain.model.ProviderAccount
import com.aiquota.app.domain.model.ProviderCredential
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    fun observeAccounts(): Flow<List<ProviderAccount>>
    suspend fun getAllAccounts(): List<ProviderAccount>
    suspend fun getAccount(id: String): ProviderAccount?
    suspend fun addAccount(providerId: String, displayName: String, connectorType: ProviderAccount.ConnectorType): ProviderAccount
    suspend fun updateAccount(account: ProviderAccount)
    suspend fun setEnabled(id: String, enabled: Boolean)
    suspend fun deleteAccount(id: String)
    suspend fun attachCredential(accountId: String, credential: ProviderCredential)
    suspend fun readCredential(accountId: String): ProviderCredential?
    suspend fun testConnection(account: ProviderAccount): AuthResult
}