package com.zhaoyingqi.payledger.ui

import java.math.BigDecimal
import java.math.RoundingMode

/** 分 → ¥x.xx（显示用，两位小数）。 */
fun formatCents(cents: Long): String =
    "¥" + BigDecimal(cents).movePointLeft(2).setScale(2, RoundingMode.HALF_UP).toPlainString()

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
