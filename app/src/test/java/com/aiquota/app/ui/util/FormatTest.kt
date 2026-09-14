package com.aiquota.app.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class FormatTest {

    @Test
    fun `百分比格式化`() {
        assertEquals("--", Format.percent(null))
        assertEquals("100%", Format.percent(100.0))
        assertEquals("0%", Format.percent(0.0))
        assertEquals("42%", Format.percent(42.4))
        assertEquals("43%", Format.percent(42.5))
    }

    @Test
    fun `数字带单位`() {
        assertEquals("--", Format.number(null, null))
        assertEquals("15", Format.number(15.0, null))
        assertEquals("15 tokens", Format.number(15.0, "tokens"))
        assertEquals("12.34 USD", Format.number(12.34, "USD"))
    }

    @Test
    fun `相对时间`() {
        val now = Instant.parse("2026-01-01T12:00:00Z")
        assertEquals("刚刚", Format.relativeTime(now.minusSeconds(30), now))
        assertEquals("2 分钟前", Format.relativeTime(now.minusSeconds(120), now))
        assertEquals("5 小时前", Format.relativeTime(now.minusSeconds(5 * 3600), now))
        assertEquals("3 天前", Format.relativeTime(now.minusSeconds(3 * 86400), now))
    }
}