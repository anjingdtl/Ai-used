package com.aiquota.app.core.network

import com.aiquota.app.core.security.SecretMasker

/** 请求日志脱敏：拦截 Authorization / 长 token */
class RedactingInterceptor : okhttp3.Interceptor {
    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        val safeUrl = SecretMasker.redact(request.url.toString())
        android.util.Log.d(TAG, ">>> ${request.method} $safeUrl")
        return runCatching { chain.proceed(request) }
            .onFailure { e ->
                android.util.Log.d(TAG, "<<< ${request.method} $safeUrl ERROR ${e.javaClass.simpleName}")
            }
            .getOrNull()!!
    }

    private companion object { const val TAG = "AiQuota-Net" }
}

/** 统一错误映射 */
fun Throwable.toQueryError(): com.aiquota.app.domain.model.QueryError {
    return when (this) {
        is javax.net.ssl.SSLException -> com.aiquota.app.domain.model.QueryError.NetworkUnavailable
        is java.net.ConnectException -> com.aiquota.app.domain.model.QueryError.NetworkUnavailable
        is java.net.UnknownHostException -> com.aiquota.app.domain.model.QueryError.NetworkUnavailable
        is java.net.SocketTimeoutException -> com.aiquota.app.domain.model.QueryError.Timeout
        is okhttp3.internal.http2.StreamResetException -> com.aiquota.app.domain.model.QueryError.NetworkUnavailable
        is retrofit2.HttpException -> mapHttp(code())
        is java.io.IOException -> com.aiquota.app.domain.model.QueryError.NetworkUnavailable
        is kotlinx.serialization.SerializationException ->
            com.aiquota.app.domain.model.QueryError.InvalidResponse(this.message)
        else -> com.aiquota.app.domain.model.QueryError.Unknown(this.message)
    }
}

private fun mapHttp(code: Int): com.aiquota.app.domain.model.QueryError = when (code) {
    in 400..499 -> when {
        code == 401 -> com.aiquota.app.domain.model.QueryError.Unauthorized()
        code == 403 -> com.aiquota.app.domain.model.QueryError.Forbidden()
        code == 429 -> com.aiquota.app.domain.model.QueryError.RateLimited()
        else -> com.aiquota.app.domain.model.QueryError.Unauthorized("HTTP $code")
    }
    in 500..599 -> com.aiquota.app.domain.model.QueryError.ServerError(code)
    else -> com.aiquota.app.domain.model.QueryError.Unknown("HTTP $code")
}