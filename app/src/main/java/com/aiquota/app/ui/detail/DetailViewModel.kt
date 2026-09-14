package com.aiquota.app.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiquota.app.domain.model.ProviderAccount
import com.aiquota.app.domain.model.ProviderId
import com.aiquota.app.domain.model.ProviderState
import com.aiquota.app.domain.repository.AccountRepository
import com.aiquota.app.domain.repository.HistoryRepository
import com.aiquota.app.domain.repository.QuotaHistoryPoint
import com.aiquota.app.domain.repository.QuotaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class DetailUiState(
    val account: ProviderAccount? = null,
    val providerId: ProviderId? = null,
    val state: ProviderState = ProviderState(accountId = ""),
    val history: List<QuotaHistoryPoint> = emptyList(),
    val isRefreshing: Boolean = false
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val accountRepository: AccountRepository,
    private val quotaRepository: QuotaRepository,
    private val historyRepository: HistoryRepository
) : ViewModel() {

    val accountId: String = checkNotNull(savedStateHandle["accountId"])
    private val _isRefreshing = MutableStateFlow(false)

    private val accountFlow: Flow<ProviderAccount?> =
        accountRepository.observeAccounts().map { list -> list.firstOrNull { it.id == accountId } }

    private val stateFlow = quotaRepository.observeState(accountId)

    /** 关键桶历史（近 30 天），随快照的关键桶 id 切换 */
    private val historyFlow: Flow<List<QuotaHistoryPoint>> =
        stateFlow.flatMapLatest { state ->
            val bucketId = state.snapshot?.buckets?.firstOrNull { it.isKeyBucket }?.id
            if (bucketId == null) flowOf(emptyList())
            else historyRepository.observePoints(
                accountId = accountId,
                bucketId = bucketId,
                since = Instant.now().minusSeconds(30L * 24 * 3600),
                until = Instant.now().plusSeconds(3600)
            )
        }

    val uiState: StateFlow<DetailUiState> =
        combine(accountFlow, stateFlow, historyFlow, _isRefreshing) {
                account, state, history, refreshing ->
            DetailUiState(
                account = account,
                providerId = account?.let { ProviderId.fromKey(it.providerId) },
                state = state,
                history = history,
                isRefreshing = refreshing
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    fun refresh() {
        viewModelScope.launch {
            if (_isRefreshing.value) return@launch
            _isRefreshing.value = true
            try { quotaRepository.refresh(accountId) }
            finally { _isRefreshing.value = false }
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            accountRepository.getAccount(accountId)?.let { accountRepository.deleteAccount(it.id) }
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { accountRepository.setEnabled(accountId, enabled) }
    }
}