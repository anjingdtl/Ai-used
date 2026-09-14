package com.aiquota.app.ui.addaccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiquota.app.domain.model.ProviderAccount
import com.aiquota.app.domain.model.ProviderId
import com.aiquota.app.domain.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddAccountViewModel @Inject constructor(
    private val accountRepository: AccountRepository
) : ViewModel() {

    /** 添加平台账户。connectorType 依据平台能力推断。 */
    fun add(providerId: ProviderId, onComplete: () -> Unit) {
        viewModelScope.launch {
            val connector = when (providerId) {
                ProviderId.DEBUG -> ProviderAccount.ConnectorType.MOCK
                ProviderId.GROK -> ProviderAccount.ConnectorType.UNAVAILABLE
                else -> ProviderAccount.ConnectorType.BRIDGE
            }
            accountRepository.addAccount(
                providerId = providerId.key,
                displayName = providerId.displayName,
                connectorType = connector
            )
            onComplete()
        }
    }
}