package com.aiquota.app.provider.bridge

import com.aiquota.app.domain.model.WindowType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 协议契约：Desktop Bridge 输出的 windowType 常量必须全部能被 Android 端
 * [WindowType.valueOf] 成功解析，禁止桥端自造 "5H" / "ROLLING_5H" / "WEEK" 等非标准值。
 * 本测试为 Android 侧协议契约的留痕，Python 侧由 bridge_self_test.py 的协议测试对应守护。
 */
class WindowTypeProtocolTest {

    /** docs/bridge-protocol.md 中正式写死的合法枚举值集合。 */
    private val protocolValues = listOf(
        "ROLLING", "ROLLING_5_HOURS", "DAILY", "WEEKLY", "MONTHLY",
        "CREDIT", "BALANCE", "TOKEN", "REQUEST_COUNT", "CUSTOM", "UNKNOWN"
    )

    @Test
    fun `all protocol window types resolve via valueOf`() {
        for (value in protocolValues) {
            val parsed = runCatching { WindowType.valueOf(value) }
            assertTrue("$value 必须能被 WindowType.valueOf 解析", parsed.isSuccess)
        }
    }

    @Test
    fun `android enum and protocol set are identical`() {
        val enumValues = WindowType.entries.map { it.name }
        assertEquals(protocolValues.toSet(), enumValues.toSet())
    }

    @Test
    fun `forbidden non-protocol aliases must NOT parse`() {
        for (alias in listOf("5H", "ROLLING_5H", "WEEK", "MONTH", "DAILY_WINDOW")) {
            val parsed = runCatching { WindowType.valueOf(alias) }
            assertTrue("非法别名 $alias 不应被解析", parsed.isFailure)
        }
    }

    @Test
    fun `validator accepts every protocol window type`() {
        for (value in protocolValues) {
            assertTrue(
                "Validator 应认可协议常量 $value",
                BridgeQuotaValidator.isKnownWindowType(value)
            )
        }
    }

    @Test
    fun `validator rejects non-protocol aliases`() {
        for (alias in listOf("5H", "ROLLING_5H", "WEEK", "")) {
            assertTrue("Validator 应拒绝非协议常量 $alias", !BridgeQuotaValidator.isKnownWindowType(alias))
        }
    }
}