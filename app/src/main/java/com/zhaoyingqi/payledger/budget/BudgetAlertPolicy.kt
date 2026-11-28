package com.zhaoyingqi.payledger.budget

/** 月度预算判定结果：是否发通知、档位（1=80% / 2=100%）、通知后应写入的新掩码。 */
data class MonthlyAlertDecision(
    val shouldNotify: Boolean,
    val level: Int,
    val newMask: Int,
) {
    companion object {
        const val LEVEL_80 = 1
        const val LEVEL_100 = 2
        val NONE = MonthlyAlertDecision(shouldNotify = false, level = 0, newMask = 0)
    }
}

/** 预算提醒判定纯函数（v08 D4）：同一档位同一个月只提醒一次，每日按自然日去重。 */
object BudgetAlertPolicy {

    /**
     * 月度判定：先看 100% 档（bit1）再看 80% 档（bit0），已达 100% 直接发高档，
     * 掩码按位合并保证同档不重复；预算 ≤ 0 不提醒。
     */
    fun evaluateMonth(spentCents: Long, monthlyCents: Long, alreadyMask: Int): MonthlyAlertDecision {
        if (monthlyCents <= 0L) return MonthlyAlertDecision.NONE
        return when {
            spentCents >= monthlyCents && alreadyMask and 0b10 == 0 ->
                MonthlyAlertDecision(true, MonthlyAlertDecision.LEVEL_100, alreadyMask or 0b10)
            spentCents * 10 >= monthlyCents * 8 && alreadyMask and 0b01 == 0 ->
                MonthlyAlertDecision(true, MonthlyAlertDecision.LEVEL_80, alreadyMask or 0b01)
            else -> MonthlyAlertDecision.NONE.copy(newMask = alreadyMask)
        }
    }

    /** 每日判定：达到日额且今天还没提醒过（alertedDate 与 today 不同）才发。 */
    fun evaluateDay(todaySpentCents: Long, dailyCents: Long, alertedDateIso: String, todayIso: String): Boolean =
        dailyCents > 0L && todaySpentCents >= dailyCents && alertedDateIso != todayIso
}
