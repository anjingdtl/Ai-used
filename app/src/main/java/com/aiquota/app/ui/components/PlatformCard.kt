package com.aiquota.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aiquota.app.domain.model.QuotaBucket
import com.aiquota.app.domain.model.QuotaType
import com.aiquota.app.domain.model.SyncStatus
import com.aiquota.app.domain.model.WindowType
import com.aiquota.app.ui.dashboard.AccountCardUi
import com.aiquota.app.ui.util.fallbackVisual
import com.aiquota.app.ui.util.syncColor
import com.aiquota.app.ui.util.visual

/** 同步状态中文标签 */
fun SyncStatus.label(): String = when (this) {
    SyncStatus.SYNCING -> "同步中"
    SyncStatus.LIVE -> "实时"
    SyncStatus.CACHED -> "缓存"
    SyncStatus.FAILED_NO_CACHE -> "无数据"
    SyncStatus.UNAVAILABLE -> "未开放"
}

/**
 * 单个平台额度卡片：品牌徽标 + 名称 + 状态 + 各额度桶。
 */
@Composable
fun PlatformCard(
    card: AccountCardUi,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val providerId = card.providerId
    val visual = if (providerId != null) providerId.visual()
    else fallbackVisual(card.account.providerId)
    val state = card.state
    val snapshot = state.snapshot

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        onClick = { onClick?.invoke() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ---- Header ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(visual.brandColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = visual.abbreviation,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = card.account.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.width(6.dp))
                        StatusDot(status = state.syncStatus)
                    }
                    snapshot?.planName?.takeIf { it.isNotBlank() }?.let { plan ->
                        Text(
                            text = plan,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                Text(
                    text = state.syncStatus.label(),
                    style = MaterialTheme.typography.labelSmall,
                    color = syncColor(state.syncStatus)
                )
            }

            Spacer(Modifier.size(16.dp))

            // ---- Body ----
            when {
                snapshot == null && state.syncStatus == SyncStatus.UNAVAILABLE -> {
                    PlaceholderText("该平台暂未开放额度查询接口")
                }
                snapshot == null -> {
                    val msg = when (state.syncStatus) {
                        SyncStatus.FAILED_NO_CACHE -> "首次查询尚未成功，下拉刷新重试"
                        SyncStatus.SYNCING -> "正在同步额度…"
                        else -> "等待同步"
                    }
                    PlaceholderText(msg, alpha = 0.7f)
                }
                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        snapshot.balance?.let { balance ->
                            QuotaBucketRow(
                                bucket = QuotaBucket(
                                    id = "balance",
                                    name = balance.name.ifBlank { "余额" },
                                    type = QuotaType.CURRENCY,
                                    remaining = balance.available,
                                    remainingPercent = null,
                                    unit = balance.unit ?: balance.currency,
                                    windowType = WindowType.BALANCE
                                )
                            )
                        }
                        snapshot.buckets.forEach { bucket ->
                            QuotaBucketRow(bucket = bucket)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusDot(status: SyncStatus) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(syncColor(status))
    )
}

@Composable
private fun PlaceholderText(text: String, alpha: Float = 1f) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.alpha(alpha)
    )
}