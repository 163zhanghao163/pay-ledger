package com.zhaoyingqi.payledger.notification

import com.zhaoyingqi.payledger.data.TransactionRecord

/**
 * 候选重复判定（v06 D2）：对同渠道、同金额、采集时间在 ±10 秒内的候选，按顺序判断——
 * 1. 双方商户都非空且不同 → 不同笔，保留；
 * 2. 双方交易时间都明确（非估算）且相差 ≥ 1 秒 → 不同笔，保留（同商户连付两笔同额的典型场景）；
 * 3. 其余 → 同一笔，丢弃。
 * 相比旧窗口判重是放宽而非收紧：只有新增信号明确指向「不同笔」时才多保留一条。
 */
object DuplicatePolicy {
    const val DISTINCT_TIME_MS = 1_000L

    fun isDuplicate(candidate: TransactionRecord, incoming: TransactionRecord): Boolean {
        val bothMerchantsKnown =
            candidate.counterparty.isNotBlank() && incoming.counterparty.isNotBlank()
        if (bothMerchantsKnown && candidate.counterparty != incoming.counterparty) return false

        val bothTimesExact = !candidate.timeEstimated && !incoming.timeEstimated
        if (bothTimesExact &&
            Math.abs(candidate.occurredAt - incoming.occurredAt) >= DISTINCT_TIME_MS
        ) {
            return false
        }
        return true
    }
}
