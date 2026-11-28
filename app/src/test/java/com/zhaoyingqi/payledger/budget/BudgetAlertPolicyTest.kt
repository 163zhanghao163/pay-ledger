package com.zhaoyingqi.payledger.budget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v08 任务 4.1：预算纯函数十条用例。 */
class BudgetAlertPolicyTest {

    // ---- 月度：80% / 100% 档位与去重 ----

    @Test
    fun month_below_80_percent_not_notified() {
        val decision = BudgetAlertPolicy.evaluateMonth(spentCents = 7_99L, monthlyCents = 10_00L, alreadyMask = 0)
        assertFalse(decision.shouldNotify)
        assertEquals(0, decision.newMask)
    }

    @Test
    fun month_reaches_80_percent_notifies_once() {
        val decision = BudgetAlertPolicy.evaluateMonth(spentCents = 8_00L, monthlyCents = 10_00L, alreadyMask = 0)
        assertTrue(decision.shouldNotify)
        assertEquals(MonthlyAlertDecision.LEVEL_80, decision.level)
        assertEquals(0b01, decision.newMask)
    }

    @Test
    fun month_reaches_100_percent_notifies_high_level() {
        val decision = BudgetAlertPolicy.evaluateMonth(spentCents = 10_00L, monthlyCents = 10_00L, alreadyMask = 0)
        assertTrue(decision.shouldNotify)
        assertEquals(MonthlyAlertDecision.LEVEL_100, decision.level)
        // 已达 100% 直接记高档掩码，不再回头发 80%
        assertEquals(0b10, decision.newMask)
    }

    @Test
    fun month_same_level_not_repeated() {
        val decision = BudgetAlertPolicy.evaluateMonth(spentCents = 9_00L, monthlyCents = 10_00L, alreadyMask = 0b01)
        assertFalse(decision.shouldNotify)
        assertEquals(0b01, decision.newMask)
    }

    @Test
    fun month_mask_resets_for_new_month() {
        // 跨月重置由 BudgetSettings.monthlyAlertedMask 完成：新月份读出掩码 0 即可再次提醒
        val decision = BudgetAlertPolicy.evaluateMonth(spentCents = 8_50L, monthlyCents = 10_00L, alreadyMask = 0)
        assertTrue(decision.shouldNotify)
        assertEquals(MonthlyAlertDecision.LEVEL_80, decision.level)
    }

    // ---- 每日：阈值与自然日去重 ----

    @Test
    fun day_notifies_when_reaching_daily_budget() {
        assertTrue(BudgetAlertPolicy.evaluateDay(todaySpentCents = 30_00L, dailyCents = 30_00L, alertedDateIso = "", todayIso = "2025-06-10"))
        // 刚好超过也算
        assertTrue(BudgetAlertPolicy.evaluateDay(todaySpentCents = 30_01L, dailyCents = 30_00L, alertedDateIso = "", todayIso = "2025-06-10"))
    }

    @Test
    fun day_same_date_not_repeated() {
        assertFalse(
            BudgetAlertPolicy.evaluateDay(todaySpentCents = 35_00L, dailyCents = 30_00L, alertedDateIso = "2025-06-10", todayIso = "2025-06-10"),
        )
    }

    @Test
    fun day_next_day_notifies_again() {
        assertTrue(
            BudgetAlertPolicy.evaluateDay(todaySpentCents = 31_00L, dailyCents = 30_00L, alertedDateIso = "2025-06-10", todayIso = "2025-06-11"),
        )
    }

    @Test
    fun day_off_or_zero_budget_not_notified() {
        assertFalse(BudgetAlertPolicy.evaluateDay(todaySpentCents = 99_00L, dailyCents = 0L, alertedDateIso = "", todayIso = "2025-06-10"))
        // 未达阈值不提醒
        assertFalse(BudgetAlertPolicy.evaluateDay(todaySpentCents = 29_99L, dailyCents = 30_00L, alertedDateIso = "", todayIso = "2025-06-10"))
    }

    // ---- BudgetPlan：切换类型保留金额 ----

    @Test
    fun switching_type_keeps_amounts_and_unset_returns_none() {
        val settings = BudgetSettings(type = BudgetType.DAILY, dailyCents = 30_00L, monthlyCents = 90_00L)
        assertTrue(BudgetPlan.current(settings) is BudgetPlan.Daily)
        // 切到月度后日额不丢，切回来仍生效
        val monthly = settings.copy(type = BudgetType.MONTHLY)
        assertEquals(90_00L, (BudgetPlan.current(monthly) as BudgetPlan.Monthly).cents)
        assertEquals(30_00L, (BudgetPlan.current(settings) as BudgetPlan.Daily).cents)
        // 关闭 / 金额 ≤ 0 返回 NONE
        assertEquals(BudgetPlan.NONE, BudgetPlan.current(settings.copy(type = BudgetType.OFF)))
        assertEquals(BudgetPlan.NONE, BudgetPlan.current(settings.copy(type = BudgetType.DAILY, dailyCents = 0L)))
        assertEquals(BudgetPlan.NONE, BudgetPlan.current(settings.copy(type = BudgetType.MONTHLY, monthlyCents = 0L)))
    }
}
