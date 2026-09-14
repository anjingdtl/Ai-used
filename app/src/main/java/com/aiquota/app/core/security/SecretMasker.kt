package com.aiquota.app.core.security

/**
 * 日志脱敏工具：把 API Key / Token 只显示首尾，中间打码。
 */
object SecretMasker {

    private val SECRET_PATTERNS = listOf(
        Regex("""sk-[A-Za-z0-9]{4,}"""),
        Regex("""[A-Za-z0-9]{32,}"""),
        Regex("""Bearer\s+\S+"""),
        Regex("""Authorization[=:]+\s*[^,\s;]+"""),
        Regex("""[A-Za-z0-9_-]{24,}""")
    )

    /** 将一个明文 token 打码 */
    fun mask(value: String?): String {
        if (value.isNullOrBlank() || value.length < 8) return "****"
        if (value.length <= 8) return value.take(4) + "****"
        return value.take(4) + "****" + value.takeLast(4)
    }

    /** 将一段文本中所有疑似 secret 打码，用于日志输出 */
    fun redact(text: String): String {
        var result = text
        for (pattern in SECRET_PATTERNS) {
            result = pattern.replace(result) { m ->
                mask(m.value)
            }
        }
        return result
    }
}