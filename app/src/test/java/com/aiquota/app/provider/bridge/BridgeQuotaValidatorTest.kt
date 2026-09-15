package com.aiquota.app.provider.bridge

import com.aiquota.app.domain.model.QueryError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0-9：Bridge 真实额度响应强校验。
 * 覆盖：缺 provider/accountId、unsupported、空 buckets、越界百分比、非法 resetAt、
 * 非法 windowType 等必须被拒绝，绝不生成空 Snapshot 后标记 LIVE。
 */
class BridgeQuotaValidatorTest {

    private fun validBucket(
        id: String = "glm-rolling_5_hours",
        name: String = "积分（滚动5小时）",
        windowType: String = "ROLLING_5_HOURS",
        remainingPercent: Double? = 63.0,
        resetAt: String? = "2026-09-15T10:20:30Z"
    ) = BridgeBucket(
        id = id, name = name, windowType = windowType,
        remainingPercent = remainingPercent, usedPercent = 37.0, resetAt = resetAt
    )

    private fun quota(
        provider: String = "glm",
        accountId: String = "acc",
        source: String = "bridge",
        buckets: List<BridgeBucket> = listOf(validBucket())
    ) = BridgeQuota(provider = provider, accountId = accountId, source = source, buckets = buckets)

    @Test
    fun `valid response passes`() {
        assertNull(BridgeQuotaValidator.validate(quota()))
    }

    @Test
    fun `missing provider rejects`() {
        val err = BridgeQuotaValidator.validate(quota(provider = ""))
        assertTrue(err is QueryError.InvalidResponse)
    }

    @Test
    fun `missing accountId rejects`() {
        val err = BridgeQuotaValidator.validate(quota(accountId = ""))
        assertTrue(err is QueryError.InvalidResponse)
    }

    @Test
    fun `unsupported source maps to ProviderUnavailable`() {
        val err = BridgeQuotaValidator.validate(quota(source = "unsupported"))
        assertTrue(err is QueryError.ProviderUnavailable)
    }

    @Test
    fun `empty buckets without balance rejects`() {
        val err = BridgeQuotaValidator.validate(quota(buckets = emptyList()))
        assertTrue(err is QueryError.InvalidResponse)
    }

    @Test
    fun `empty buckets with valid balance passes`() {
        val q = quota(buckets = emptyList()).copy(
            balance = BridgeBalance(available = 100.0, currency = "CNY")
        )
        assertNull(BridgeQuotaValidator.validate(q))
    }

    @Test
    fun `bucket missing name rejects`() {
        val err = BridgeQuotaValidator.validate(quota(buckets = listOf(validBucket(name = ""))))
        assertTrue(err is QueryError.InvalidResponse)
    }

    @Test
    fun `remaining percent over 100 rejects`() {
        val err = BridgeQuotaValidator.validate(
            quota(buckets = listOf(validBucket(remainingPercent = 150.0)))
        )
        assertTrue(err is QueryError.InvalidResponse)
    }

    @Test
    fun `used percent under -1 rejects`() {
        val err = BridgeQuotaValidator.validate(
            quota(buckets = listOf(validBucket().copy(usedPercent = -50.0)))
        )
        assertTrue(err is QueryError.InvalidResponse)
    }

    @Test
    fun `tiny negative percent is tolerated`() {
        // -0.00001 之类浮点噪声允许上层归一为 0，不判非法
        assertEquals(
            null,
            BridgeQuotaValidator.validate(
                quota(buckets = listOf(validBucket(remainingPercent = -0.00001)))
            )
        )
    }

    @Test
    fun `unparseable resetAt rejects`() {
        val err = BridgeQuotaValidator.validate(
            quota(buckets = listOf(validBucket(resetAt = "1200")))
        )
        assertTrue(err is QueryError.InvalidResponse)
    }

    @Test
    fun `non-protocol windowType rejects`() {
        val err = BridgeQuotaValidator.validate(
            quota(buckets = listOf(validBucket(windowType = "5H")))
        )
        assertTrue(err is QueryError.InvalidResponse)
    }

    @Test
    fun `blank windowType rejects`() {
        val err = BridgeQuotaValidator.validate(
            quota(buckets = listOf(validBucket(windowType = "")))
        )
        assertTrue(err is QueryError.InvalidResponse)
    }
}