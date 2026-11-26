package com.zhaoyingqi.payledger.ui

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** 分 → ¥x.xx（显示用，两位小数）。 */
fun formatCents(cents: Long): String =
    "¥" + BigDecimal(cents).movePointLeft(2).setScale(2, RoundingMode.HALF_UP).toPlainString()

private val CLOCK_FMT = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")

/** 记录时间 → HH:mm（列表展示用）。 */
fun formatClock(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(epochMillis).atZone(zone).format(CLOCK_FMT)

fun formatDate(date: LocalDate): String = date.format(DATE_FMT)

fun formatLocalTime(time: LocalTime): String = time.format(CLOCK_FMT)

/** Material3 DatePicker 的选中值是「UTC 零点毫秒」，与本地日期互转必须固定用 UTC。 */
fun localDateToPickerMillis(date: LocalDate): Long =
    date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun pickerMillisToLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

/** 本地日期 + 时间 ↔ 数据库存的 epochMillis（系统时区）。 */
fun localDateTimeToEpochMillis(
    date: LocalDate,
    time: LocalTime,
    zone: ZoneId = ZoneId.systemDefault(),
): Long = date.atTime(time).atZone(zone).toInstant().toEpochMilli()

fun epochMillisToLocalDateTime(
    epochMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): LocalDateTime = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDateTime()

private val AMOUNT_INPUT = Regex("""(\d{1,8})(\.\d{1,2})?""")

/** 手动输入金额（元，可带 ¥ 前缀）→ 分；非法或非正数返回 null。 */
fun parseAmountToCents(input: String): Long? {
    val s = input.trim().removePrefix("¥").removePrefix("￥").trim()
    if (s.isEmpty()) return null
    val m = AMOUNT_INPUT.matchEntire(s) ?: return null
    val yuan = m.groupValues[1].toLong()
    val cents = m.groupValues[2].removePrefix(".").padEnd(2, '0').ifEmpty { "00" }.toLong()
    val total = yuan * 100 + cents
    return if (total > 0) total else null
}
