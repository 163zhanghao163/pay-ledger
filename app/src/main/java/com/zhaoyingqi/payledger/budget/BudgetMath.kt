package com.zhaoyingqi.payledger.budget

import java.time.LocalDate

/** 预算换算纯函数（v08 D9）。 */
object BudgetMath {

    /**
     * 每日预算换算整月总额：日额 × 当月天数（30 天 × 30 元 = 900 元、2 月 28 天 = 840 元）。
     */
    fun monthTotalCents(dailyCents: Long, month: java.time.YearMonth): Long =
        dailyCents * month.lengthOfMonth()

    /** 每日预算的月度进度数据：换算总额 + 剩余每日可用。 */
    data class DailyRemaining(val remainingPerDayCents: Long, val overspendCents: Long)

    /**
     * 剩余每日可用 = (月总额 − 已支出) ÷ 剩余天数（含今天，恒 ≥ 1），向下取整；
     * 已超支返回 (0, 超支金额)，不再折算每日额度。
     */
    fun remainingPerDayCents(monthTotalCents: Long, spentCents: Long, today: LocalDate): DailyRemaining {
        val daysLeft = java.time.YearMonth.from(today).lengthOfMonth() - (today.dayOfMonth - 1)
        val remaining = monthTotalCents - spentCents
        if (remaining <= 0L) return DailyRemaining(0L, -remaining)
        return DailyRemaining(remaining / daysLeft, 0L)
    }
}
