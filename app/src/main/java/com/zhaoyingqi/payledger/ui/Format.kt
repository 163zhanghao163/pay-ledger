package com.zhaoyingqi.payledger.ui

import java.math.BigDecimal
import java.math.RoundingMode

/** 分 → ¥x.xx（显示用，两位小数）。 */
fun formatCents(cents: Long): String =
    "¥" + BigDecimal(cents).movePointLeft(2).setScale(2, RoundingMode.HALF_UP).toPlainString()
