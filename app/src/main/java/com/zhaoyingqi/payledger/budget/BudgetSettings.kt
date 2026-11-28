package com.zhaoyingqi.payledger.budget

import android.content.SharedPreferences
import java.time.YearMonth

/** 预算类型（v08 D3）：关闭 / 每日 / 每月。 */
enum class BudgetType {
    OFF,
    DAILY,
    MONTHLY,
}

/**
 * 预算配置（v08 D3，SharedPreferences）：每日与每月金额分开存，切换类型不丢值；
 * 切换类型不重置当月提醒掩码，避免同一个月因来回切换被重复提醒。
 */
data class BudgetSettings(
    val type: BudgetType = BudgetType.OFF,
    val dailyCents: Long = 0,
    val monthlyCents: Long = 0,
) {
    companion object {
        const val KEY_TYPE = "budget_type"
        const val KEY_DAILY_CENTS = "budget_daily_cents"
        const val KEY_MONTHLY_CENTS = "budget_monthly_cents"

        /** 月度提醒掩码：值存 "yyyy-MM:mask"，bit0 = 已发 80%，bit1 = 已发 100%（跨月自动视为 0）。 */
        const val KEY_MONTHLY_ALERTED = "budget_alerted_yyyy_mm"

        /** 当日额度已提醒日期：值 "yyyy-MM-dd"。 */
        const val KEY_DAILY_ALERTED_DATE = "budget_daily_alerted_date"

        fun load(prefs: SharedPreferences): BudgetSettings {
            val type = runCatching {
                BudgetType.valueOf(prefs.getString(KEY_TYPE, BudgetType.OFF.name) ?: BudgetType.OFF.name)
            }.getOrDefault(BudgetType.OFF)
            return BudgetSettings(
                type = type,
                dailyCents = prefs.getLong(KEY_DAILY_CENTS, 0L),
                monthlyCents = prefs.getLong(KEY_MONTHLY_CENTS, 0L),
            )
        }

        fun writeTo(prefs: SharedPreferences, settings: BudgetSettings) {
            prefs.edit()
                .putString(KEY_TYPE, settings.type.name)
                .putLong(KEY_DAILY_CENTS, settings.dailyCents)
                .putLong(KEY_MONTHLY_CENTS, settings.monthlyCents)
                .apply()
        }

        /** 读取月度提醒掩码：存储的月份与当前不同视为未提醒（跨月重置）。 */
        fun monthlyAlertedMask(prefs: SharedPreferences, month: YearMonth): Int {
            val raw = prefs.getString(KEY_MONTHLY_ALERTED, null) ?: return 0
            val (storedMonth, mask) = raw.split(":").let { it.getOrNull(0) to it.getOrNull(1)?.toIntOrNull() }
            return if (storedMonth == month.toString()) mask ?: 0 else 0
        }

        /** 写月度提醒掩码，commit 同步落盘避免「通知发了、状态没存」（v08 D4）。 */
        fun writeMonthlyAlertedMask(prefs: SharedPreferences, month: YearMonth, mask: Int) {
            prefs.edit().putString(KEY_MONTHLY_ALERTED, "${month}:$mask").commit()
        }

        fun dailyAlertedDate(prefs: SharedPreferences): String =
            prefs.getString(KEY_DAILY_ALERTED_DATE, null).orEmpty()

        fun writeDailyAlertedDate(prefs: SharedPreferences, dateIso: String) {
            prefs.edit().putString(KEY_DAILY_ALERTED_DATE, dateIso).commit()
        }
    }
}
