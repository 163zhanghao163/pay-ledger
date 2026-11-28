package com.zhaoyingqi.payledger.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** v06 任务 3.1：去重键的稳定性与区分度。 */
class DedupKeyTest {
    private val t0 = 1_764_000_000_000L // 任意基准时间

    @Test
    fun same_input_same_key() {
        val a = DedupKey.of("WECHAT", 999, "星巴克", t0)
        val b = DedupKey.of("WECHAT", 999, "星巴克", t0)
        assertEquals(a, b)
        assertEquals(16, a.length)
    }

    @Test
    fun different_merchant_different_key() {
        val a = DedupKey.of("WECHAT", 999, "星巴克", t0)
        val b = DedupKey.of("WECHAT", 999, "瑞幸咖啡", t0)
        assertNotEquals(a, b)
    }

    @Test
    fun cross_minute_bucket_different_key() {
        // 同一笔的秒级偏差（59s → 01s）跨分钟桶：键不同，是已知边界，由窗口判重兜底
        val a = DedupKey.of("WECHAT", 999, "星巴克", t0 + 59_000)
        val b = DedupKey.of("WECHAT", 999, "星巴克", t0 + 61_000)
        assertNotEquals(a, b)
    }

    @Test
    fun same_minute_bucket_same_key() {
        val a = DedupKey.of("ALIPAY", 500, "便利店", t0)
        val b = DedupKey.of("ALIPAY", 500, "便利店", t0 + 30_000)
        assertEquals(a, b)
    }

    @Test
    fun empty_counterparty_still_stable() {
        assertEquals(
            DedupKey.of("WECHAT", 100, "", t0),
            DedupKey.of("WECHAT", 100, "", t0),
        )
    }
}
