package com.zhaoyingqi.payledger.notification

import org.junit.Assert.assertEquals
import org.junit.Test

/** bg 任务 1.2：影子比对只对「旧流程入账、新流程未入账」计 1，其余计 0。 */
class ShadowCompareTest {
    private val parsed = ParsedPayment(
        channel = Channel.WECHAT,
        amountCents = 999,
        counterparty = "测试商户",
        summary = "微信付款 · 测试商户",
    )

    @Test
    fun old_recorded_new_missed_counts_one() {
        assertEquals(1, ShadowCompare.diffCount(oldParsed = parsed, newParsed = null))
    }

    @Test
    fun both_missed_counts_zero() {
        assertEquals(0, ShadowCompare.diffCount(oldParsed = null, newParsed = null))
    }

    @Test
    fun both_recorded_counts_zero() {
        assertEquals(0, ShadowCompare.diffCount(oldParsed = parsed, newParsed = parsed))
    }

    @Test
    fun only_new_recorded_counts_zero() {
        // 实际不会出现（新流程是旧流程的子集），但契约上只有「旧有新无」计差异
        assertEquals(0, ShadowCompare.diffCount(oldParsed = null, newParsed = parsed))
    }

    @Test
    fun description_never_contains_raw_text() {
        // 差异明细只写结构化摘要，不携带通知原文（隐私最小化）
        assertEquals("影子差异：旧流程入账、新流程未入账", ShadowCompare.DESCRIPTION)
    }
}
