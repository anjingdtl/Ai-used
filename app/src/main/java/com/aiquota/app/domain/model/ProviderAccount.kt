package com.aiquota.app.domain.model

import java.time.Instant

/** 平台账户（不存储真实 Secret，只存 credentialRef） */
data class ProviderAccount(
    val id: String,
    val providerId: String,
    val displayName: String,
    val connectorType: ConnectorType,
    val enabled: Boolean = true,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    /** 查询方式 */
    enum class ConnectorType { DIRECT_API, BRIDGE, MOCK, UNAVAILABLE }
}