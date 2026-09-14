package com.aiquota.app.domain.model

/** 同步状态 */
enum class SyncStatus {
    /** 正在同步（网络查询中） */
    SYNCING,
    /** 实时数据 */
    LIVE,
    /** 缓存数据（查询失败，保留历史） */
    CACHED,
    /** 首次查询失败且无缓存 */
    FAILED_NO_CACHE,
    /** 平台暂未开放接口（非错误，能力缺失） */
    UNAVAILABLE
}