package com.zhaoyingqi.payledger.notification

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** v06 任务 4.2：三种格式 + 信任区间。 */
class PaymentTimeParserTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val arrival = LocalDateTime.of(2026, 11, 27, 15, 0, 0).atZone(zone).toInstant().toEpochMilli()

    private fun expected(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun full_date_with_seconds() {
        assertEquals(
            expected(2026, 11, 27, 12, 30) + 5_000,
            PaymentTimeParser.parse("2026-11-27 12:30:05", arrival, zone),
        )
    }

    @Test
    fun full_date_without_seconds() {
        assertEquals(
            expected(2026, 11, 27, 12, 30),
            PaymentTimeParser.parse("2026-11-27 12:30", arrival, zone),
        )
    }

    @Test
    fun month_day_same_year() {
        // 到达 15:00 的前 23 小时内：11月26日 16:00 在信任区间
        assertEquals(
            expected(2026, 11, 26, 16, 0),
            PaymentTimeParser.parse("11月26日 16:00", arrival, zone),
        )
    }

    @Test
    fun month_day_cross_year_falls_back_to_previous_year() {
        // 12月31日 的账单在 1月1日 到达：按到达年不在信任区间，回退上一年
        val arrivalNewYear = LocalDateTime.of(2027, 1, 1, 0, 30).atZone(zone).toInstant().toEpochMilli()
        assertEquals(
            expected(2026, 12, 31, 23, 50),
            PaymentTimeParser.parse("12月31日 23:50", arrivalNewYear, zone),
        )
    }

    @Test
    fun today_format() {
        val today = java.time.Instant.ofEpochMilli(arrival).atZone(zone).toLocalDate()
        assertEquals(
            expected(today.year, today.monthValue, today.dayOfMonth, 14, 5),
            PaymentTimeParser.parse("今天 14:05", arrival, zone),
        )
    }

    @Test
    fun outside_trust_interval_returns_null() {
        // 早于 24h
        assertNull(PaymentTimeParser.parse("2026-11-25 12:00:00", arrival, zone))
        // 晚于 +5min
        assertNull(PaymentTimeParser.parse("2026-11-27 15:30:00", arrival, zone))
    }

    @Test
    fun month_day_far_outside_interval_returns_null() {
        assertNull(PaymentTimeParser.parse("1月1日 09:00", arrival, zone))
    }

    @Test
    fun no_time_returns_null() {
        assertNull(PaymentTimeParser.parse("付款成功 ¥20.00", arrival, zone))
        assertNull(PaymentTimeParser.parse("", arrival, zone))
    }
}
