package com.aiquota.app.domain.model

import java.time.Instant

/**
 * 统一的动态额度桶。所有厂商额度都能归一到该模型，
 * 禁止在业务层写死 fiveHourQuota / weeklyQuota 等字段。
 */
data class QuotaBucket(
    val id: String,
    val name: String,
    val type: QuotaType,
    val used: Double? = null,
    val limit: Double? = null,
    val remaining: Double? = null,
    val usedPercent: Double? = null,
    val remainingPercent: Double? = null,
    val unit: String? = null,
    val windowType: WindowType = WindowType.UNKNOWN,
    val windowStartAt: Instant? = null,
    val resetAt: Instant? = null
) {
    /** 是否可用于顶部最低额度统计（必须有 remainingPercent） */
    val isKeyBucket: Boolean
        get() = remainingPercent != null && windowType != WindowType.UNKNOWN
}