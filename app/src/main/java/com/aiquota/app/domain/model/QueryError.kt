package com.aiquota.app.domain.model

/**
 * 查询错误分类。UI 只展示用户能看懂的中文，
 * 禁止直接输出底层异常名。
 */
sealed class QueryError : Exception() {
    object NetworkUnavailable : QueryError()
    object Timeout : QueryError()
    data class Unauthorized(val detail: String? = null) : QueryError()
    data class Forbidden(val detail: String? = null) : QueryError()
    data class RateLimited(val retryAfterSeconds: Long? = null) : QueryError()
    data class ServerError(val code: Int? = null) : QueryError()
    data class InvalidResponse(val detail: String? = null) : QueryError()
    object BridgeOffline : QueryError()
    object ProviderUnavailable : QueryError() // 平台未开放接口
    data class Unknown(val detail: String? = null) : QueryError()

    fun userMessage(): String = when (this) {
        NetworkUnavailable -> "网络不可用"
        Timeout -> "连接超时"
        is Unauthorized -> "凭据无效或已失效"
        is Forbidden -> "权限不足，无法访问"
        is RateLimited -> "请求过于频繁，请稍后再试"
        is ServerError -> "服务暂时不可用"
        is InvalidResponse -> "数据解析失败"
        BridgeOffline -> "Bridge 未连接"
        ProviderUnavailable -> "该平台暂未开放额度接口"
        is Unknown -> selfOrUnknown(detail)
    }

    private fun selfOrUnknown(detail: String?): String =
        if (detail.isNullOrBlank()) "未知错误" else "未知错误：$detail"
}