package com.aiquota.app.domain.model

/** 额度窗口类型，设计为可扩展，新增厂商无需改主结构 */
enum class WindowType {
    ROLLING,
    ROLLING_5_HOURS,
    DAILY,
    WEEKLY,
    MONTHLY,
    CREDIT,
    BALANCE,
    TOKEN,
    REQUEST_COUNT,
    CUSTOM,
    UNKNOWN;

    val isOneOfTheKnownWindows: Boolean
        get() = this != UNKNOWN

    /** 中文显示名 */
    fun displayName(): String = when (this) {
        ROLLING -> "滚动窗口"
        ROLLING_5_HOURS -> "5小时"
        DAILY -> "每日"
        WEEKLY -> "每周"
        MONTHLY -> "每月"
        CREDIT -> "Credits"
        BALANCE -> "余额"
        TOKEN -> "Token"
        REQUEST_COUNT -> "请求次数"
        CUSTOM -> "自定义"
        UNKNOWN -> "未知窗口"
    }
}