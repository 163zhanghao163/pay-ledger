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
import androidx.compose.ui.text.style.TextOverflow
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
 * 明细页预算进度（v08 4.5/D9，v1.0 压缩为单行紧凑样式）：
 * 每日 → 一行「¥30/天 · 余 ¥28/天 · 还剩 12 天」，超支变红提示；
 * 每月 → 一行摘要 + 4dp 进度条；未设置（NONE）→ 一行轻量设置入口。
 * 原两行大卡片占位过多挤压记录列表（v1.0 用户反馈）。
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
            BudgetCompactCard(
                modifier = modifier,
                summaryLine = "每日预算 ${formatCents(plan.cents)}/天 · 余 ${formatCents(remaining.remainingPerDayCents)}/天" +
                    " · 还剩 $daysLeft 天",
                overspend = remaining.overspendCents > 0L,
                onEdit = onEdit,
            )
        }
        is BudgetPlan.Monthly -> {
            BudgetCompactCard(
                modifier = modifier,
                summaryLine = "每月预算 · 本月已用 ${formatCents(monthSpentCents)} / ${formatCents(plan.cents)}",
                overspend = monthSpentCents > plan.cents,
                onEdit = onEdit,
            ) {
                val scheme = MaterialTheme.colorScheme
                val overspent = monthSpentCents > plan.cents
                val fraction = if (plan.cents > 0) monthSpentCents.toFloat() / plan.cents else 0f
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier.fillMaxWidth().height(4.dp)
                        .clip(CircleShape)
                        .background(scheme.surfaceContainerHighest),
                ) {
                    Box(
                        Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(4.dp)
                            .clip(CircleShape)
                            .background(if (overspent) scheme.error else scheme.primary),
                    )
                }
            }
        }
        // 未设置预算：单行轻量入口（否则用户找不到预算功能在哪配置）
        BudgetPlan.NONE -> BudgetCompactCard(
            modifier = modifier,
            summaryLine = "预算 · 未设置",
            overspend = false,
            onEdit = onEdit,
        )
    }
}

/** 紧凑预算卡：单行摘要（超支变红）+ 右上「设置」按钮；[content] 可选（月预算进度条）。 */
@Composable
private fun BudgetCompactCard(
    modifier: Modifier = Modifier,
    summaryLine: String,
    overspend: Boolean,
    onEdit: () -> Unit,
    content: @Composable () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = scheme.surfaceContainerLow,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    summaryLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (overspend) scheme.error else scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                BudgetEditButton(onEdit)
            }
            content()
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
