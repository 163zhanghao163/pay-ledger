package com.zhaoyingqi.payledger.notification

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 从付款文案解析交易时间（v06 D7）。支持三种格式：
 * 1. `yyyy-MM-dd HH:mm[:ss]`（完整日期）
 * 2. `M月d日 HH:mm`（无年份，按到达时间所在年推断，跨年时回退上一年）
 * 3. `今天 HH:mm`
 *
 * 解析结果必须落在信任区间 `[到达时间 − 24h, 到达时间 + 5min]` 内：
 * 超出视为不可信（历史订单列表、营销文案等），返回 null，由调用方回退到达时间并标记估算。
 */
object PaymentTimeParser {
    const val TRUST_BEFORE_MS = 24 * 60 * 60 * 1000L
    const val TRUST_AFTER_MS = 5 * 60 * 1000L

    /** 完整日期：2026-11-27 12:30 或 2026-11-27 12:30:05 */
    private val FULL_DATE = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})[ T](\d{1,2}):(\d{2})(?::(\d{2}))?""")

    /** 无年份：12月25日 09:30 */
    private val MONTH_DAY = Regex("""(\d{1,2})月(\d{1,2})日\s*(\d{1,2}):(\d{2})""")

    /** 今天 HH:mm */
    private val TODAY = Regex("""今天\s*(\d{1,2}):(\d{2})""")

    fun parse(text: String, receivedAt: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (text.isBlank()) return null
        val lowerBound = receivedAt - TRUST_BEFORE_MS
        val upperBound = receivedAt + TRUST_AFTER_MS

        fun trusted(epoch: Long): Long? = epoch.takeIf { it in lowerBound..upperBound }

        FULL_DATE.find(text)?.let { m ->
            val g = m.groupValues
            val year = g.getOrNull(1)?.toIntOrNull() ?: return null
            val month = g.getOrNull(2)?.toIntOrNull() ?: return null
            val day = g.getOrNull(3)?.toIntOrNull() ?: return null
            val hour = g.getOrNull(4)?.toIntOrNull() ?: return null
            val minute = g.getOrNull(5)?.toIntOrNull() ?: return null
            val second = g.getOrNull(6)?.toIntOrNull() ?: 0
            val at = runCatching {
                LocalDateTime.of(year, month, day, hour, minute, second)
                    .atZone(zone).toInstant().toEpochMilli()
            }.getOrNull() ?: return null
            return trusted(at)
        }

        MONTH_DAY.find(text)?.let { m ->
            val g = m.groupValues
            val month = g.getOrNull(1)?.toIntOrNull() ?: return null
            val day = g.getOrNull(2)?.toIntOrNull() ?: return null
            val hour = g.getOrNull(3)?.toIntOrNull() ?: return null
            val minute = g.getOrNull(4)?.toIntOrNull() ?: return null
            val arrivalDate = Instant.ofEpochMilli(receivedAt).atZone(zone).toLocalDate()
            // 到达所在年优先；跨年（如 12月31日 的账单在 1月1日 到达）时尝试上一年。
            for (year in intArrayOf(arrivalDate.year, arrivalDate.year - 1)) {
                val at = runCatching {
                    LocalDate.of(year, month, day).atTime(hour, minute)
                        .atZone(zone).toInstant().toEpochMilli()
                }.getOrNull() ?: continue
                trusted(at)?.let { return it }
            }
            return null
        }

        TODAY.find(text)?.let { m ->
            val g = m.groupValues
            val hour = g.getOrNull(1)?.toIntOrNull() ?: return null
            val minute = g.getOrNull(2)?.toIntOrNull() ?: return null
            val at = Instant.ofEpochMilli(receivedAt).atZone(zone).toLocalDate()
                .atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
            return trusted(at)
        }

        return null
    }
}
