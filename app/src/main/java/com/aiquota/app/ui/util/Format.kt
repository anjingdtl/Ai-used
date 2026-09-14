package com.aiquota.app.ui.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** UI 格式化工具 */
object Format {

    fun percent(value: Double?): String = when {
        value == null -> "--"
        value >= 100 -> "100%"
        value <= 0 -> "0%"
        else -> "${value.roundPercent()}%"
    }

    fun Double.roundPercent(): Int = kotlin.math.floor(this + 0.5).toInt()

    /** 带单位的数值，保留合理小数 */
    fun number(value: Double?, unit: String?): String {
        if (value == null) return "--"
        val text = when {
            value % 1.0 == 0.0 -> value.toLong().toString()
            else -> (kotlin.math.round(value * 100) / 100.0).toString()
        }
        return if (unit.isNullOrBlank()) text else "$text $unit"
    }

    /** 相对时间：刚刚 / x 分钟前 / x 小时前 / x 天前 / 日期 */
    fun relativeTime(instant: Instant?, now: Instant = Instant.now()): String {
        if (instant == null) return "暂无数据"
        val delta = ChronoUnit.SECONDS.between(instant, now)
        return when {
            delta < 0 -> "刚刚"
            delta < 60 -> "刚刚"
            delta < 3600 -> "${(delta / 60)} 分钟前"
            delta < 86400 -> "${(delta / 3600)} 小时前"
            delta < 604800 -> "${(delta / 86400)} 天前"
            else -> instant.atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("M月d日"))
        }
    }

    /** 下次重置时间：今天显示 HH:mm，跨天显示 M月d日 HH:mm */
    fun resetTime(instant: Instant?, now: Instant = Instant.now()): String {
        if (instant == null) return "--"
        val zone = ZoneId.systemDefault()
        val target = instant.atZone(zone)
        val today = now.atZone(zone).toLocalDate()
        val pattern = if (target.toLocalDate() == today) "HH:mm" else "M月d日 HH:mm"
        return target.format(DateTimeFormatter.ofPattern(pattern))
    }

    /** 全量日期时间 */
    fun fullTime(instant: Instant?): String {
        if (instant == null) return "--"
        return instant.atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    }
}