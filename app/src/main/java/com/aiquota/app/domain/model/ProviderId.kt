package com.aiquota.app.domain.model

/** 受支持平台标识。新增平台时在此追加，并实现对应 ProviderAdapter。 */
enum class ProviderId(val key: String, val displayName: String) {
    OPENAI_CODEX("openai_codex", "ChatGPT / Codex"),
    GLM("glm", "GLM Coding Plan"),
    MINIMAX("minimax", "MiniMax"),
    OPENCODE_GO("opencode", "OpenCode Go"),
    GROK("grok", "Grok / xAI"),
    // debug / 测试专用，仅存在于 debug 构建
    DEBUG("debug", "Debug");

    companion object {
        fun fromKey(key: String): ProviderId? = entries.firstOrNull { it.key == key }
    }
}