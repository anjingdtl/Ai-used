package com.aiquota.app.domain.model

/** 额度类型 */
enum class QuotaType {
    PERCENT,
    COUNT,
    TOKEN,
    CURRENCY,
    CREDIT,
    REQUEST,
    UNKNOWN
}