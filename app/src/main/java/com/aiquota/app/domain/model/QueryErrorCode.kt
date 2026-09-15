package com.aiquota.app.domain.model

/**
 * QueryError <-> 持久化字符串 编解码。
 * 用于把最近同步的错误写入 sync_event 并重建为可展示的错误对象，
 * 避免 UI 只看到一条无结构的文本。
 */
object QueryErrorCode {

    private const val SEP = "|"

    fun encode(e: QueryError): String = "${code(e)}$SEP${e.userMessage()}"

    fun decode(raw: String?): QueryError? {
        if (raw.isNullOrBlank()) return null
        val i = raw.indexOf(SEP)
        val code = if (i > 0) raw.substring(0, i) else raw
        val detail = if (i > 0) raw.substring(i + 1) else null
        return when (code) {
            CODE_NETWORK -> QueryError.NetworkUnavailable
            CODE_TIMEOUT -> QueryError.Timeout
            CODE_UNAUTHORIZED -> QueryError.Unauthorized(detail)
            CODE_FORBIDDEN -> QueryError.Forbidden(detail)
            CODE_RATE_LIMITED -> QueryError.RateLimited()
            CODE_SERVER -> QueryError.ServerError()
            CODE_INVALID_RESPONSE -> QueryError.InvalidResponse(detail)
            CODE_BRIDGE_OFFLINE -> QueryError.BridgeOffline
            CODE_UNAVAILABLE -> QueryError.ProviderUnavailable
            else -> QueryError.Unknown(detail)
        }
    }

    private fun code(e: QueryError): String = when (e) {
        is QueryError.NetworkUnavailable -> CODE_NETWORK
        QueryError.Timeout -> CODE_TIMEOUT
        is QueryError.Unauthorized -> CODE_UNAUTHORIZED
        is QueryError.Forbidden -> CODE_FORBIDDEN
        is QueryError.RateLimited -> CODE_RATE_LIMITED
        is QueryError.ServerError -> CODE_SERVER
        is QueryError.InvalidResponse -> CODE_INVALID_RESPONSE
        QueryError.BridgeOffline -> CODE_BRIDGE_OFFLINE
        QueryError.ProviderUnavailable -> CODE_UNAVAILABLE
        is QueryError.Unknown -> CODE_UNKNOWN
    }

    private const val CODE_NETWORK = "NETWORK"
    private const val CODE_TIMEOUT = "TIMEOUT"
    private const val CODE_UNAUTHORIZED = "UNAUTHORIZED"
    private const val CODE_FORBIDDEN = "FORBIDDEN"
    private const val CODE_RATE_LIMITED = "RATE_LIMITED"
    private const val CODE_SERVER = "SERVER"
    private const val CODE_INVALID_RESPONSE = "INVALID_RESPONSE"
    private const val CODE_BRIDGE_OFFLINE = "BRIDGE_OFFLINE"
    private const val CODE_UNAVAILABLE = "UNAVAILABLE"
    private const val CODE_UNKNOWN = "UNKNOWN"
}