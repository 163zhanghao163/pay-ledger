package com.zhaoyingqi.payledger.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zhaoyingqi.payledger.budget.BudgetMath
import com.zhaoyingqi.payledger.budget.BudgetPlan
import com.zhaoyingqi.payledger.budget.BudgetSettings
import com.zhaoyingqi.payledger.budget.BudgetType
import com.zhaoyingqi.payledger.ui.formatCents
import java.time.LocalDate
import java.time.YearMonth

/**
 * 明细页预算进度（v08 4.5/D9）：
 * 每日 →「剩余每天可用 X · 本月还剩 N 天」，超支附加「已超支 X 元」；
 * 每月 →「本月已用 / 预算」进度条；未设置（NONE）时显示轻量设置入口——
 * 原实现「不渲染」导致无处发现预算功能，已配置的卡片也补「设置」便于修改。
 * 月支出与日期由 VM 的 dateBoundaryFlow 驱动，跨零点自动重算，不轮询。
 */
@Composable
internal fun BudgetProgress(
    settings: BudgetSettings,
    monthSpentCents: Long,
    today: LocalDate,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    when (val plan = BudgetPlan.current(settings)) {
        is BudgetPlan.Daily -> {
            val month = YearMonth.from(today)
            val monthTotal = BudgetMath.monthTotalCents(plan.cents, month)
            val remaining = BudgetMath.remainingPerDayCents(monthTotal, monthSpentCents, today)
            val daysLeft = month.lengthOfMonth() - (today.dayOfMonth - 1)
            Surface(
                modifier = modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = scheme.surfaceContainerLow,
                border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.45f)),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("每日预算", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "预算 ${formatCents(plan.cents)}/天",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                        )
                        BudgetEditButton(onEdit)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "剩余每天可用 ${formatCents(remaining.remainingPerDayCents)} · 本月还剩 $daysLeft 天",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (remaining.overspendCents > 0L) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "已超支 ${formatCents(remaining.overspendCents)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.error,
                        )
                    }
                }
            }
        }
        is BudgetPlan.Monthly -> {
            val fraction = if (plan.cents > 0) monthSpentCents.toFloat() / plan.cents else 0f
            val overspent = monthSpentCents > plan.cents
            val description = "本月已用 ${formatCents(monthSpentCents)}，预算 ${formatCents(plan.cents)}"
            Surface(
                modifier = modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = scheme.surfaceContainerLow,
                border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.45f)),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("每月预算", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.weight(1f))
                        Text(
                            description,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (overspent) scheme.error else scheme.onSurfaceVariant,
                        )
                        BudgetEditButton(onEdit)
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier.fillMaxWidth().height(6.dp)
                            .clip(CircleShape)
                            .background(scheme.surfaceContainerHighest),
                    ) {
                        Box(
                            Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp)
                                .clip(CircleShape)
                                .background(if (overspent) scheme.error else scheme.primary),
                        )
                    }
                }
            }
        }
        // 未设置预算：不再整体隐藏，显示轻量入口（否则用户找不到预算功能在哪配置）
        BudgetPlan.NONE -> Surface(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = scheme.surfaceContainerLow,
            border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.45f)),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("预算", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.weight(1f))
                    BudgetEditButton(onEdit)
                }
                Text(
                    "未设置。设置每日或每月预算后，这里会显示进度并在接近上限时提醒。",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 卡片右上角的「设置」按钮（压缩内边距，避免撑高卡片）。 */
@Composable
private fun BudgetEditButton(onEdit: () -> Unit) {
    TextButton(
        onClick = onEdit,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
    ) { Text("设置") }
}

private val previewDaily = BudgetSettings(type = BudgetType.DAILY, dailyCents = 30_00L)
private val previewMonthly = BudgetSettings(type = BudgetType.MONTHLY, monthlyCents = 900_00L)
private val previewNone = BudgetSettings(type = BudgetType.OFF)

@Preview(name = "每日预算（亮）", showBackground = true)
@Composable
private fun BudgetDailyLightPreview() {
    MaterialTheme {
        BudgetProgress(previewDaily, monthSpentCents = 200_00L, today = LocalDate.of(2025, 6, 12), onEdit = {})
    }
}

@Preview(name = "每日预算（暗）", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BudgetDailyDarkPreview() {
    MaterialTheme {
        BudgetProgress(previewDaily, monthSpentCents = 200_00L, today = LocalDate.of(2025, 6, 12), onEdit = {})
    }
}

@Preview(name = "每月预算超支（亮）", showBackground = true)
@Composable
private fun BudgetMonthlyLightPreview() {
    MaterialTheme {
        BudgetProgress(previewMonthly, monthSpentCents = 950_00L, today = LocalDate.of(2025, 6, 12), onEdit = {})
    }
}

@Preview(name = "每月预算（暗）", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BudgetMonthlyDarkPreview() {
    MaterialTheme {
        BudgetProgress(previewMonthly, monthSpentCents = 500_00L, today = LocalDate.of(2025, 6, 12), onEdit = {})
    }
}

@Preview(name = "未设置（亮）", showBackground = true)
@Composable
private fun BudgetNoneLightPreview() {
    MaterialTheme {
        BudgetProgress(previewNone, monthSpentCents = 200_00L, today = LocalDate.of(2025, 6, 12), onEdit = {})
    }
}
