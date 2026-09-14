package com.aiquota.app.domain.model

/** 快照数据来源 */
enum class DataSource {
    NETWORK,
    BRIDGE,
    CACHE,
    MOCK // 仅 debug/preview/test
}