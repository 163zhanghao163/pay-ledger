package com.zhaoyingqi.payledger.debug

import com.zhaoyingqi.payledger.data.TransactionRecord
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * v09 D4 性能基线测试数据：确定性生成覆盖近 12 个月的 1 万条记录。
 * 入口只在 debug 调试页（release 构建不显示该卡片，任务书 3.1）。
 * counterparty 统一用 [MARKER]，清理时只删这些行，不触碰真实记录。
 */
object PerfTestData {
    const val MARKER = "性能测试商户"
    const val ROWS = 10_000

    fun generate(
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<TransactionRecord> = List(ROWS) { i ->
        val occurredAt = LocalDateTime.of(
            today.minusDays((i % 365).toLong()),
            LocalTime.of((i % 24), (i * 7) % 60),
        ).atZone(zone).toInstant().toEpochMilli()
        val isIncome = i % 5 == 0
        TransactionRecord(
            amountCents = (i % 4900L) + 100L,
            channel = if (i % 2 == 0) "WECHAT" else "ALIPAY",
            counterparty = MARKER,
            occurredAt = occurredAt,
            summary = if (isIncome) "测试收款" else "测试付款",
            createdAt = occurredAt,
            type = if (isIncome) TransactionRecord.TYPE_INCOME else TransactionRecord.TYPE_EXPENSE,
            source = TransactionRecord.SOURCE_MANUAL,
            category = when (i % 6) {
                0 -> "餐饮"
                1 -> "交通"
                2 -> "购物"
                3 -> "日用"
                4 -> "娱乐"
                else -> null
            },
            dedupKey = null,
            capturedAt = occurredAt,
        )
    }
}
