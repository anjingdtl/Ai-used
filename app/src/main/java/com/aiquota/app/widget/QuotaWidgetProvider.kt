package com.aiquota.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.aiquota.app.MainActivity
import com.aiquota.app.R
import com.aiquota.app.domain.repository.QuotaRepository
import com.aiquota.app.ui.util.Format
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 桌面小组件：展示「当前最低的关键额度百分比」。
 *
 * Lifctime（P1-4）：
 *  - 使用 [goAsync] + [android.appwidget.AppWidgetProvider] 的 PendingResult，保证
 *    onReceive 返回后协程仍在执行、并在 finally 中 finish()，杜绝"new Scope 后立即返回"的脆弱写法；
 *  - 数据只读 Last Known Good（本地 Room 缓存），断网同样显示，不直接联网；
 *  - 点击打开 [MainActivity]。
 */
@AndroidEntryPoint
class QuotaWidgetProvider : AppWidgetProvider() {

    @Inject
    lateinit var quotaRepository: QuotaRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (appWidgetIds.isEmpty()) return
        val pending = goAsync()
        scope.launch {
            try {
                renderAll(context, appWidgetManager, appWidgetIds)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        super.onReceive(context, intent)
        if (intent?.action == ACTION_UPDATE && context != null) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, QuotaWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                val pending = goAsync()
                scope.launch {
                    try {
                        renderAll(context, mgr, ids)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    private suspend fun renderAll(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val snapshot = quotaRepository.observeEnabledStates().first()
            .mapNotNull { it.snapshot }
            .minByOrNull { it.minRemainingPercent ?: Double.MAX_VALUE }
        val base = baseViews(context)
        for (id in ids) {
            val views = base.clone()
            if (snapshot == null || snapshot.minRemainingPercent == null) {
                views.setTextViewText(R.id.widget_percent, "--")
                views.setTextViewText(
                    R.id.widget_subtitle,
                    context.getString(R.string.widget_none)
                )
            } else {
                views.setTextViewText(R.id.widget_percent, Format.percent(snapshot.minRemainingPercent))
                views.setTextViewText(
                    R.id.widget_subtitle,
                    context.getString(
                        R.string.widget_updated,
                        snapshot.accountName,
                        Format.relativeTime(snapshot.queriedAt)
                    )
                )
            }
            mgr.updateAppWidget(id, views)
        }
    }

    /** 组装统一基础视图：点击打开主界面（只读缓存，不联网）。 */
    private fun baseViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_quota)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, open)
        return views
    }

    companion object {
        const val ACTION_UPDATE = "com.aiquota.app.widget.ACTION_UPDATE"

        /** 触发小组件刷新。若本机已添加小组件则发广播让其重新读取缓存。 */
        @JvmStatic
        fun requestUpdate(context: Context) {
            context.sendBroadcast(
                Intent(context, QuotaWidgetProvider::class.java).setAction(ACTION_UPDATE)
            )
        }
    }
}