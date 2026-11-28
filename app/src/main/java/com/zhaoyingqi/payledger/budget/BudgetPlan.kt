package com.zhaoyingqi.payledger.budget

/** 当前生效的预算计划（v08 4.1）：关闭或对应金额 ≤ 0 都视为未设置。 */
sealed interface BudgetPlan {
    data object NONE : BudgetPlan
    data class Daily(val cents: Long) : BudgetPlan
    data class Monthly(val cents: Long) : BudgetPlan

    companion object {
        /** 未设置返回空（NONE）：金额非法时 UI 不渲染进度、提醒不触发。 */
        fun current(settings: BudgetSettings): BudgetPlan = when (settings.type) {
            BudgetType.DAILY -> if (settings.dailyCents > 0) Daily(settings.dailyCents) else NONE
            BudgetType.MONTHLY -> if (settings.monthlyCents > 0) Monthly(settings.monthlyCents) else NONE
            BudgetType.OFF -> NONE
        }
    }
}
