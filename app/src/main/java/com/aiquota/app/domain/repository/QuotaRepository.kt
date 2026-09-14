package com.aiquota.app.domain.repository

import com.aiquota.app.domain.model.ProviderState
import com.aiquota.app.domain.model.QuotaSnapshot
import kotlinx.coroutines.flow.Flow

interface QuotaRepository {
    /** 所有启用账户的实时 UI 状态 */
    fun observeEnabledStates(): Flow<List<ProviderState>>

    /** 单个账户状态 */
    fun observeState(accountId: String): Flow<ProviderState>

    /** 读最后成功快照（无网络时立即展示） */
    suspend fun getLastGoodSnapshot(accountId: String): QuotaSnapshot?

    /** 并行刷新所有启用账户 */
    suspend fun refreshAll()

    /** 刷新单个账户 */
    suspend fun refresh(accountId: String)

    /** 保留本快照（成功查询后持久化） */
    suspend fun persistSnapshot(snapshot: QuotaSnapshot)
}