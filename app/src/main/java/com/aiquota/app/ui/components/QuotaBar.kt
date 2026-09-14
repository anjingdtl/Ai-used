package com.aiquota.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aiquota.app.domain.model.QuotaBucket
import com.aiquota.app.ui.util.Format
import com.aiquota.app.ui.util.quotaColorOf

/**
 * 单个额度桶的展示行：名称 + 剩余百分比 + 进度条 + 已用/上限 + 下次重置。
 */
@Composable
fun QuotaBucketRow(
    bucket: QuotaBucket,
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val color = quotaColorOf(bucket.remainingPercent)
    val subtitle = buildString {
        val used = Format.number(bucket.used, bucket.unit)
        val limit = Format.number(bucket.limit, bucket.unit)
        append("已用 ").append(used)
        if (limit != "--") { append(" / ").append(limit) }
        if (bucket.resetAt != null) {
            append("  ·  重置 ").append(Format.resetTime(bucket.resetAt))
        }
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = bucket.name.ifBlank { bucket.windowType.displayName() },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = Format.percent(bucket.remainingPercent),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(trackColor),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val pct = (((bucket.remainingPercent ?: 0.0) / 100.0).coerceIn(0.0, 1.0)).toFloat()
            if (pct > 0f) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth(pct)
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(color)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            maxLines = 1
        )
    }
}