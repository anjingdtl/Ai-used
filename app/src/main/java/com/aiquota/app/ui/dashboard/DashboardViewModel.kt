package com.aiquota.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiquota.app.domain.model.DashboardOverview
import com.aiquota.app.domain.model.ProviderAccount
import com.aiquota.app.domain.model.ProviderId
import com.aiquota.app.domain.model.ProviderState
import com.aiquota.app.domain.repository.AccountRepository
import com.aiquota.app.domain.repository.QuotaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 单个平台卡片 UI 数据 */
data class AccountCardUi(
    val account: ProviderAccount,
    val providerId: ProviderId?,
    val state: ProviderState
)

/** Dashboard 页面完整状态 */
data class DashboardUiState(
    val cards: List<AccountCardUi> = emptyList(),
    val overview: DashboardOverview = DashboardOverview(
        minRemainingPercent = null, totalEnabled = 0, liveCount = 0,
        cachedCount = 0, syncingCount = 0, unavailableCount = 0, updatedAt = null
    ),
    val isRefreshing: Boolean = false,
    val refreshError: String? = null
) {
    val isEmpty: Boolean get() = cards.isEmpty()
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val quotaRepository: QuotaRepository
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    private val _refreshError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<DashboardUiState> = combine(
        accountRepository.observeAccounts(),
        quotaRepository.observeEnabledStates(),
        _isRefreshing,
        _refreshError
    ) { accounts, states, refreshing, error ->
        val stateById = states.associateBy { it.accountId }
        val cards = accounts
            .filter { it.enabled }
            .map { acct ->
                AccountCardUi(
                    account = acct,
                    providerId = ProviderId.fromKey(acct.providerId),
                    state = stateById[acct.id] ?: ProviderState(accountId = acct.id)
                )
            }
        DashboardUiState(
            cards = cards,
            overview = buildOverview(cards),
            isRefreshing = refreshing,
            refreshError = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState()
    )

    fun refreshAll() {
        viewModelScope.launch {
            if (_isRefreshing.value) return@launch
            _isRefreshing.value = true
            _refreshError.value = null
            try {
                quotaRepository.refreshAll()
            } catch (e: Exception) {
                _refreshError.value = e.message ?: "刷新失败"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private fun buildOverview(cards: List<AccountCardUi>): DashboardOverview {
        val available = cards.mapNotNull { it.state }
        val live = available.filter { it.syncStatus.name == "LIVE" }
        val cached = available.filter { it.syncStatus.name == "CACHED" }
        val syncing = available.filter { it.syncStatus.name == "SYNCING" }
        val unavailable = available.filter { it.syncStatus.name == "UNAVAILABLE" }
        val minPercent = cards
            .mapNotNull { it.state.snapshot?.minRemainingPercent }
            .minOrNull()
        val lastUpdated = cards
            .mapNotNull { it.state.lastSuccessAt }
            .maxOrNull()
        return DashboardOverview(
            minRemainingPercent = minPercent,
            totalEnabled = cards.size,
            liveCount = live.size,
            cachedCount = cached.size,
            syncingCount = syncing.size,
            unavailableCount = unavailable.size,
            updatedAt = lastUpdated
        )
    }
}