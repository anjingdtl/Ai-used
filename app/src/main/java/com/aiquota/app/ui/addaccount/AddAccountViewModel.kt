package com.aiquota.app.ui.addaccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiquota.app.domain.model.AuthResult
import com.aiquota.app.domain.model.CredentialType
import com.aiquota.app.domain.model.ProviderAccount
import com.aiquota.app.domain.model.ProviderCredential
import com.aiquota.app.domain.model.ProviderId
import com.aiquota.app.domain.model.QueryError
import com.aiquota.app.domain.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 账号配置流程的测试状态。 */
sealed interface ConnectionTestState {
    object Idle : ConnectionTestState
    object Testing : ConnectionTestState
    data class Success(val planName: String?) : ConnectionTestState
    data class Failure(val message: String) : ConnectionTestState
}

@HiltViewModel
class AddAccountViewModel @Inject constructor(
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _selectedProvider = MutableStateFlow<ProviderId?>(null)
    val selectedProvider: StateFlow<ProviderId?> = _selectedProvider.asStateFlow()

    private val _testState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val testState: StateFlow<ConnectionTestState> = _testState.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    /** 进入平台配置。connectorType 依据平台真实能力预设。 */
    fun select(providerId: ProviderId) {
        _selectedProvider.value = providerId
        _testState.value = ConnectionTestState.Idle
    }

    /** 返回平台选择列表，清空表单状态。 */
    fun backToList() {
        _selectedProvider.value = null
        _testState.value = ConnectionTestState.Idle
    }

    fun defaultConnector(providerId: ProviderId): ProviderAccount.ConnectorType = when (providerId) {
        ProviderId.DEBUG -> ProviderAccount.ConnectorType.MOCK
        ProviderId.GROK -> ProviderAccount.ConnectorType.UNAVAILABLE
        else -> ProviderAccount.ConnectorType.BRIDGE
    }

    /** 配置页需要输入连接凭据（Bridge 地址 + Secret / Direct API Key）。 */
    fun needsCredential(providerId: ProviderId): Boolean =
        defaultConnector(providerId) == ProviderAccount.ConnectorType.BRIDGE

    /** 凭据输入变化时清零测试结果，要求重新测试。 */
    fun resetTest() {
        _testState.value = ConnectionTestState.Idle
    }

    fun testConnection(
        providerId: ProviderId,
        bridgeUrl: String,
        bridgeSecret: String
    ) {
        val credential = buildBridgeCredential(providerId, bridgeUrl, bridgeSecret)
        _testState.value = ConnectionTestState.Testing
        viewModelScope.launch {
            val result = accountRepository.testCredential(providerId.key, credential)
            _testState.value = when (result) {
                is AuthResult.Success -> ConnectionTestState.Success(result.planName)
                is AuthResult.Failure -> ConnectionTestState.Failure(result.error.userMessage())
            }
        }
    }

    fun save(
        providerId: ProviderId,
        displayName: String,
        bridgeUrl: String,
        bridgeSecret: String,
        onSaved: (ProviderAccount) -> Unit
    ) {
        viewModelScope.launch {
            _saving.value = true
            val connector = defaultConnector(providerId)
            val account = accountRepository.addAccount(
                providerId = providerId.key,
                displayName = displayName.ifBlank { providerId.displayName },
                connectorType = connector
            )
            val credential = if (connector == ProviderAccount.ConnectorType.BRIDGE) {
                buildBridgeCredential(providerId, bridgeUrl, bridgeSecret)
            } else {
                ProviderCredential(providerId = providerId.key, type = CredentialType.MOCK)
            }
            accountRepository.attachCredential(account.id, credential)
            _saving.value = false
            onSaved(account)
        }
    }

    private fun buildBridgeCredential(providerId: ProviderId, url: String, secret: String): ProviderCredential {
        val cleanUrl = url.trim().removeSuffix("/")
        return ProviderCredential(
            providerId = providerId.key,
            type = CredentialType.BRIDGE,
            secret = cleanUrl,
            extra = mapOf(
                "bridgeUrl" to cleanUrl,
                "bridgeSecret" to secret.trim()
            )
        )
    }
}