package com.zhaoyingqi.payledger.budget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** v08 任务 4.2：BudgetMath 换算算例。 */
class BudgetMathTest {

    @Test
    fun daily_30_yuan_over_30_days_is_900() {
        // 4 月恰好 30 天
        assertEquals(900_00L, BudgetMath.monthTotalCents(30_00L, YearMonth.of(2025, 4)))
    }

    @Test
    fun daily_30_yuan_over_feb_28_days_is_840() {
        assertEquals(840_00L, BudgetMath.monthTotalCents(30_00L, YearMonth.of(2025, 2)))
    }

    @Test
    fun remaining_880_divided_by_29_days_floors_to_30_34() {
        // 30 天月份的第 2 天 → 剩余 29 天；88000 分 ÷ 29 = 3034.48 → 向下取整 3034（30.34 元）
        val result = BudgetMath.remainingPerDayCents(90_000L, spentCents = 2_000L, today = LocalDate.of(2025, 4, 2))
        assertEquals(3_034L, result.remainingPerDayCents)
        assertEquals(0L, result.overspendCents)
    }

    @Test
    fun remaining_820_divided_by_29_days_floors_to_28_27() {
        // 82000 ÷ 29 = 2827.58 → 2827（28.27 元）
        val result = BudgetMath.remainingPerDayCents(90_000L, spentCents = 8_000L, today = LocalDate.of(2025, 4, 2))
        assertEquals(2_827L, result.remainingPerDayCents)
    }

    @Test
    fun last_day_of_month_counts_only_today() {
        // 月末最后一天：剩余天数 = 1，剩余额度全部算今天可用
        val result = BudgetMath.remainingPerDayCents(90_000L, spentCents = 80_000L, today = LocalDate.of(2025, 1, 31))
        assertEquals(1L, 31 - (31 - 1)) // 语义标注：剩余天数含今天
        assertEquals(10_000L, result.remainingPerDayCents)
    }

    @Test
    fun overspend_returns_zero_and_overspend_amount() {
        val result = BudgetMath.remainingPerDayCents(90_000L, spentCents = 91_000L, today = LocalDate.of(2025, 1, 15))
        assertEquals(0L, result.remainingPerDayCents)
        assertEquals(1_000L, result.overspendCents)
    }
}
