package com.aiquota.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
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
 * 数据直接取自本地缓存（Last Known Good），断网同样可显示；同步成功后也会刷新。
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
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, RemoteViews(context.packageName, R.layout.widget_quota))
            renderLoading(context, appWidgetManager, id)
        }
        refreshAsync(context, appWidgetManager, appWidgetIds)
    }

    private fun refreshAsync(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        scope.launch {
            val min = runCatching {
                quotaRepository.observeEnabledStates().first()
                    .mapNotNull { it.snapshot?.minRemainingPercent }
                    .minOrNull()
            }.getOrNull()

            for (id in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_quota)
                val percent = if (min == null) "--" else Format.percent(min)
                views.setTextViewText(R.id.widget_percent, percent)
                views.setTextViewText(R.id.widget_subtitle, subtitleFor(min, context))
                appWidgetManager.updateAppWidget(id, views)
            }
        }
    }

    private fun subtitleFor(minPct: Double?, context: Context): String =
        if (minPct == null) context.getString(R.string.widget_none)
        else context.getString(R.string.widget_lowest)

    private fun renderLoading(
        context: Context,
        appWidgetManager: AppWidgetManager,
        id: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_quota)
        views.setTextViewText(R.id.widget_percent, "…")
        views.setTextViewText(R.id.widget_subtitle, context.getString(R.string.widget_syncing))
        appWidgetManager.updateAppWidget(id, views)
    }

    companion object {
        const val ACTION_UPDATE = "com.aiquota.app.widget.ACTION_UPDATE"

        /** 触发小组件刷新。若本机已添加小组件则发广播让其重新读取缓存。 */
        @JvmStatic
        fun requestUpdate(context: Context) {
            context.sendBroadcast(
                android.content.Intent(context, QuotaWidgetProvider::class.java)
                    .setAction(ACTION_UPDATE)
            )
        }
    }

    override fun onReceive(context: Context?, intent: android.content.Intent?) {
        super.onReceive(context, intent)
        if (intent?.action == ACTION_UPDATE && context != null) {
            AppWidgetManager.getInstance(context).let { mgr ->
                val ids = mgr.getAppWidgetIds(
                    android.content.ComponentName(context, QuotaWidgetProvider::class.java)
                )
                if (ids.isNotEmpty()) {
                    renderLoading(context, mgr, ids.first())
                    refreshAsync(context, mgr, ids)
                }
            }
        }
    }
}