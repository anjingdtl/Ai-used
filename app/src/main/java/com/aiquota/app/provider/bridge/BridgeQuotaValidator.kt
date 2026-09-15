package com.aiquota.app.provider.bridge

import com.aiquota.app.domain.model.QueryError
import com.aiquota.app.domain.model.WindowType

/**
 * Bridge 真实额度响应强校验（P0-3 / P0-9）。
 *
 * 规则：
 *  - provider / accountId 必须非空；
 *  - source == "unsupported" 视为平台未开放 -> [QueryError.ProviderUnavailable]；
 *  - 真实可用响应必须至少有一个合法 Bucket 或非空 Balance，否则视为非法响应，
 *    禁止生成空 Snapshot 后标记 LIVE；
 *  - 每个 bucket 的 usedPercent / remainingPercent 必须落在 [-1, 100] 区间
 *    （极小的 -0.00001 之类允许归一，150% 之类服务端异常则拒绝）；
 *  - resetAt 可解析为 ISO 时间；
 *  - windowType 必须能被 [WindowType.valueOf] 解析（协议常量契约）。
 */
object BridgeQuotaValidator {

    /** 校验整个响应；返回 null 表示合法，否则返回拒绝原因所对应的 [QueryError]。 */
    fun validate(quota: BridgeQuota): QueryError? {
        if (quota.provider.isBlank() || quota.accountId.isBlank()) {
            return QueryError.InvalidResponse("Bridge 响应缺少 provider/accountId")
        }
        if (quota.source.equals("unsupported", ignoreCase = true)) {
            return QueryError.ProviderUnavailable
        }
        val hasBucket = quota.buckets.any { it.id.isNotBlank() && it.name.isNotBlank() }
        val hasBalance = quota.balance != null && quota.balance!!.available >= 0.0
        if (!hasBucket && !hasBalance) {
            return QueryError.InvalidResponse("Bridge 响应未包含任何额度项")
        }
        for (b in quota.buckets) {
            val err = validateBucket(b)
            if (err != null) return err
        }
        return null
    }

    /** 校验单个 bucket，返回 null 表示合法。 */
    fun validateBucket(b: BridgeBucket): QueryError? {
        if (b.id.isBlank() || b.name.isBlank()) {
            return QueryError.InvalidResponse("Bucket 缺少 id/name")
        }
        for (pct in listOf(b.usedPercent, b.remainingPercent)) {
            if (pct != null) {
                // 极小的负浮点偏差（如 -0.00001）允许上层归一为 0；
                // 明显的越界（如 150%）属于服务端异常，拒绝而非静默纠错。
                if (pct < 0.0 && pct < -1.0) {
                    return QueryError.InvalidResponse("使用的百分比越界: $pct")
                }
                if (pct > 100.0) {
                    return QueryError.InvalidResponse("使用的百分比越界: $pct")
                }
            }
        }
        if (b.resetAt != null && parseIso(b.resetAt) == null) {
            return QueryError.InvalidResponse("resetAt 无法解析: ${b.resetAt}")
        }
        if (!isKnownWindowType(b.windowType)) {
            return QueryError.InvalidResponse("未知 windowType: ${b.windowType}")
        }
        return null
    }

    /** windowType 是否属于协议统一契约（可被 Android 枚举解析）。空串视为协议违约。 */
    fun isKnownWindowType(name: String): Boolean {
        if (name.isBlank()) return false
        return runCatching { WindowType.valueOf(name) }.getOrNull() != null
    }
}