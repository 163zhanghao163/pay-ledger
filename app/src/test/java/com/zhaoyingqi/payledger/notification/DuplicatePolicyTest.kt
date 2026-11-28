package com.zhaoyingqi.payledger.notification

import com.zhaoyingqi.payledger.data.TransactionRecord
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v06 任务 3.2：D2 判定顺序的四类用例。 */
class DuplicatePolicyTest {

    private fun record(
        counterparty: String,
        occurredAt: Long,
        timeEstimated: Boolean = false,
    ) = TransactionRecord(
        amountCents = 2000,
        channel = "WECHAT",
        counterparty = counterparty,
        occurredAt = occurredAt,
        summary = "微信付款",
        createdAt = occurredAt,
        timeEstimated = timeEstimated,
        capturedAt = occurredAt,
    )

    /** 双通道捕获同一笔：商户相同、文案时间相同（解析自同一页面）→ 重复。 */
    @Test
    fun dual_channel_same_payment_is_duplicate() {
        val t = 1_764_000_000_000L
        assertTrue(DuplicatePolicy.isDuplicate(record("星巴克", t), record("星巴克", t)))
    }

    /** 同商户同额、两笔文案各带付款时间（相差 ≥1 秒）→ 不同笔，保留。 */
    @Test
    fun same_merchant_two_payments_one_second_apart_not_duplicate() {
        val t = 1_764_000_000_000L
        assertFalse(DuplicatePolicy.isDuplicate(record("星巴克", t), record("星巴克", t + 1_100)))
    }

    /** 相差不足 1 秒视为同一笔（窗口兜底）。 */
    @Test
    fun same_merchant_sub_second_is_duplicate() {
        val t = 1_764_000_000_000L
        assertTrue(DuplicatePolicy.isDuplicate(record("星巴克", t), record("星巴克", t + 500)))
    }

    /** 商户不同（双方都非空）→ 不同笔，即使时间相同。 */
    @Test
    fun different_merchants_not_duplicate() {
        val t = 1_764_000_000_000L
        assertFalse(DuplicatePolicy.isDuplicate(record("星巴克", t), record("瑞幸咖啡", t)))
    }

    /** 任一方时间为估算 → 不参与时间比较，落回「同一笔」。 */
    @Test
    fun estimated_time_does_not_participate() {
        val t = 1_764_000_000_000L
        assertTrue(
            DuplicatePolicy.isDuplicate(
                record("星巴克", t, timeEstimated = true),
                record("星巴克", t + 60_000, timeEstimated = false),
            ),
        )
        assertTrue(
            DuplicatePolicy.isDuplicate(
                record("星巴克", t, timeEstimated = false),
                record("星巴克", t + 60_000, timeEstimated = true),
            ),
        )
    }

    /** 一方商户为空（解析不到）→ 商户信号不生效，其余按时间判定。 */
    @Test
    fun blank_merchant_falls_back_to_time() {
        val t = 1_764_000_000_000L
        assertTrue(DuplicatePolicy.isDuplicate(record("", t), record("星巴克", t)))
        assertFalse(DuplicatePolicy.isDuplicate(record("", t), record("星巴克", t + 2_000)))
    }
}
