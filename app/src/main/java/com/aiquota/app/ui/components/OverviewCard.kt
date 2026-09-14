package com.aiquota.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aiquota.app.domain.model.DashboardOverview
import com.aiquota.app.ui.theme.PrimaryBlue
import com.aiquota.app.ui.util.Format
import com.aiquota.app.ui.util.quotaColorOf

/**
 * 顶部汇总卡：全局最低剩余百分比 + 各连接状态计数 + 更新时间。
 */
@Composable
fun OverviewCard(
    overview: DashboardOverview,
    modifier: Modifier = Modifier
) {
    val minColor = quotaColorOf(overview.minRemainingPercent)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent
    ) {
        Column(modifier = Modifier.background(Brush.horizontalGradient(listOf(PrimaryBlue, Color(0xFF2B6CB0)))).padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "最低剩余额度",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = Format.percent(overview.minRemainingPercent),
                        color = minColor,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "已启用 ${overview.totalEnabled} 个平台",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(6.dp))
                    StatusCountRow(overview)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "最后更新 " + Format.relativeTime(overview.updatedAt),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun StatusCountRow(overview: DashboardOverview) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        CountChip(text = "实时 ${overview.liveCount}", color = Color(0xFF22C55E))
        CountChip(text = "缓存 ${overview.cachedCount}", color = Color(0xFF9CA3AF))
        CountChip(text = "未开放 ${overview.unavailableCount}", color = Color(0xFF94A3B8))
    }
}

@Composable
private fun CountChip(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.18f)) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(
                Modifier
                    .width(6.dp)
                    .height(6.dp)
                    .background(color, RoundedCornerShape(50))
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = text,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}