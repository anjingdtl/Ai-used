package com.aiquota.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 刷新间隔模型测试：真实毫秒值、各档正确、MANUAL 语义（停止前台定时刷新）。
 */
class RefreshIntervalTest {

    @Test
    fun `各档持续时间反映真实毫秒`() {
        assertEquals(30_000L, RefreshInterval.S30S.durationMillis)
        assertEquals(60_000L, RefreshInterval.MIN1.durationMillis)
        assertEquals(120_000L, RefreshInterval.MIN2.durationMillis)
        assertEquals(300_000L, RefreshInterval.MIN5.durationMillis)
        assertEquals(0L, RefreshInterval.MANUAL.durationMillis)
    }

    @Test
    fun `所有档位值互不相同`() {
        val values = RefreshInterval.entries.map { it.durationMillis }.toSet()
        assertEquals(RefreshInterval.entries.size, values.size)
    }

    @Test
    fun `MANUAL 不触发定时刷新`() {
        assertTrue(RefreshInterval.MANUAL.durationMillis == 0L)
    }
}