package com.aiquota.app.ui.util

import androidx.compose.ui.graphics.Color
import com.aiquota.app.domain.model.ProviderId

/** 各平台的品牌徽标颜色与缩写字，便于快速识别 */
data class ProviderVisual(
    val brandColor: Color,
    val abbreviation: String
)

fun ProviderId.visual(): ProviderVisual = when (this) {
    ProviderId.OPENAI_CODEX -> ProviderVisual(Color(0xFF10A37F), "C")
    ProviderId.GLM -> ProviderVisual(Color(0xFF2B6CB0), "智")
    ProviderId.MINIMAX -> ProviderVisual(Color(0xFF6D28D9), "M")
    ProviderId.OPENCODE_GO -> ProviderVisual(Color(0xFF0EA5E9), "G")
    ProviderId.GROK -> ProviderVisual(Color(0xFF111114), "x")
    ProviderId.DEBUG -> ProviderVisual(Color(0xFF64748B), "D")
}

/** 已知 providerKey 未匹配时的兜底 */
fun fallbackVisual(key: String): ProviderVisual =
    ProviderVisual(Color(0xFF64748B), key.take(1).uppercase().ifBlank { "?" })