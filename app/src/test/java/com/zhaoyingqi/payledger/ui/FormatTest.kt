package com.zhaoyingqi.payledger.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class FormatTest {

    @Test fun yuan_decimal() {
        assertEquals(1250L, parseAmountToCents("12.5"))
    }

    @Test fun yuan_two_decimals() {
        assertEquals(12800L, parseAmountToCents("128.00"))
    }

    @Test fun yuan_integer() {
        assertEquals(2000L, parseAmountToCents("20"))
    }

    @Test fun currency_prefix_accepted() {
        assertEquals(2000L, parseAmountToCents("¥20"))
        assertEquals(1234L, parseAmountToCents("￥12.34"))
    }

    @Test fun fractional_cents_padded() {
        assertEquals(5L, parseAmountToCents("0.05"))
    }

    @Test fun zero_rejected() {
        assertNull(parseAmountToCents("0"))
        assertNull(parseAmountToCents("0.00"))
    }

    @Test fun invalid_rejected() {
        assertNull(parseAmountToCents(""))
        assertNull(parseAmountToCents("abc"))
        assertNull(parseAmountToCents("12.345"))
        assertNull(parseAmountToCents("-5"))
    }

    // —— 日期/时间选择与展示 ——
    @Test fun picker_date_round_trip() {
        val date = LocalDate.of(2026, 11, 24)
        assertEquals(date, pickerMillisToLocalDate(localDateToPickerMillis(date)))
    }

    @Test fun local_date_time_round_trip() {
        val zone = ZoneId.of("Asia/Shanghai")
        val date = LocalDate.of(2026, 11, 24)
        val time = LocalTime.of(9, 30)
        val millis = localDateTimeToEpochMillis(date, time, zone)
        val back = epochMillisToLocalDateTime(millis, zone)
        assertEquals(date, back.toLocalDate())
        assertEquals(time, back.toLocalTime())
    }

    @Test fun clock_formatted_in_given_zone() {
        val zone = ZoneId.of("Asia/Shanghai")
        val millis = localDateTimeToEpochMillis(LocalDate.of(2026, 11, 24), LocalTime.of(8, 5), zone)
        assertEquals("08:05", formatClock(millis, zone))
    }
}
