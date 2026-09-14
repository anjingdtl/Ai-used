package com.aiquota.app.ui.util

import androidx.compose.ui.graphics.Color
import com.aiquota.app.domain.model.SyncStatus
import com.aiquota.app.ui.theme.QuotaCaution
import com.aiquota.app.ui.theme.QuotaCritical
import com.aiquota.app.ui.theme.QuotaEmpty
import com.aiquota.app.ui.theme.QuotaSafe
import com.aiquota.app.ui.theme.QuotaWarning
import com.aiquota.app.ui.theme.OnlineGreen
import com.aiquota.app.ui.theme.OfflineGray

/** 根据剩余百分比返回状态色 */
fun quotaColorOf(remainingPercent: Double?): Color = when {
    remainingPercent == null -> QuotaEmpty
    remainingPercent >= 50 -> QuotaSafe
    remainingPercent >= 30 -> QuotaCaution
    remainingPercent >= 10 -> QuotaWarning
    else -> QuotaCritical
}

/** 连接状态色 */
fun syncColor(status: SyncStatus): Color = when (status) {
    SyncStatus.LIVE -> OnlineGreen
    SyncStatus.CACHED, SyncStatus.UNAVAILABLE -> OfflineGray
    SyncStatus.SYNCING -> QuotaCaution
    SyncStatus.FAILED_NO_CACHE -> QuotaCritical
}