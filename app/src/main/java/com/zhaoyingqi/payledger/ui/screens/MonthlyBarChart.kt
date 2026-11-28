package com.zhaoyingqi.payledger.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zhaoyingqi.payledger.data.MonthSum
import com.zhaoyingqi.payledger.ui.BarChartGeometry
import com.zhaoyingqi.payledger.ui.formatCents

/** 月度趋势卡（v08 2.1 拆分自 StatsScreen）：12 根柱 + 每根柱的读屏语义。 */
@Composable
internal fun TrendCard(
    sums: List<MonthSum>,
    highlightMonth: Int?,
    maxAmount: Long,
    onBarTap: (Int) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("月度趋势", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "点击柱形查看该月明细",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "峰值 ${formatCents(maxAmount)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(12.dp))
            MonthlyBarChart(
                sums = sums,
                highlightMonth = highlightMonth,
                onBarTap = onBarTap,
                modifier = Modifier.fillMaxWidth().height(168.dp),
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                (1..12).forEach { month ->
                    Text(
                        "$month",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * 12 根月度柱（v08 2.3/D6）：Canvas 绘制 + 触摸经 [BarChartGeometry.indexAt] 命中；
 * 语义层每根柱一个节点（不拦截触摸），读屏可逐月朗读并通过 onClick 无障碍操作选中。
 */
@Composable
private fun MonthlyBarChart(
    sums: List<MonthSum>,
    highlightMonth: Int?,
    onBarTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val byMonth = remember(sums) { sums.associate { it.month to it.totalCents } }
    val maxSum = remember(sums) { sums.maxOfOrNull { it.totalCents } ?: 0L }
    val barGap = 6.dp

    Box(modifier) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(byMonth, maxSum) {
                detectTapGestures { pos ->
                    val index = BarChartGeometry.indexAt(pos.x, size.width.toFloat(), barGap.toPx())
                    if (index >= 0) onBarTap(index + 1)
                }
            },
        ) {
            val gap = barGap.toPx()
            val barWidth = (size.width - gap * (BarChartGeometry.BAR_COUNT - 1)) / BarChartGeometry.BAR_COUNT
            val radius = CornerRadius(5.dp.toPx())
            (1..12).forEach { month ->
                val x = (month - 1) * (barWidth + gap)
                drawRoundRect(
                    color = track.copy(alpha = 0.55f),
                    topLeft = Offset(x, 0f),
                    size = Size(barWidth, size.height),
                    cornerRadius = radius,
                )
                val total = (byMonth[month] ?: 0L).toFloat()
                if (total > 0f && maxSum > 0L) {
                    val height = (total / maxSum * size.height)
                        .coerceAtLeast(4.dp.toPx())
                        .coerceAtMost(size.height)
                    drawRoundRect(
                        color = if (month == highlightMonth) primary else primary.copy(alpha = 0.5f),
                        topLeft = Offset(x, size.height - height),
                        size = Size(barWidth, height),
                        cornerRadius = radius,
                    )
                }
            }
        }
        // 语义层：与触摸等分区域一致（v08 D6 风险注记），高 168dp ≥ 48dp
        Row(Modifier.fillMaxSize()) {
            (1..12).forEach { month ->
                val amount = byMonth[month] ?: 0L
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics {
                            role = Role.Button
                            contentDescription = "${month}月，支出 ${formatCents(amount)}"
                            onClick("查看${month}月明细") {
                                onBarTap(month)
                                true
                            }
                        },
                )
            }
        }
    }
}

@Preview(name = "月度趋势（亮）", showBackground = true)
@Composable
private fun TrendCardLightPreview() {
    TrendCardPreview()
}

@Preview(name = "月度趋势（暗）", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TrendCardDarkPreview() {
    TrendCardPreview()
}

@Composable
private fun TrendCardPreview() {
    MaterialTheme {
        TrendCard(
            sums = (1..12).map { MonthSum(it, if (it % 3 == 0) (it * 100_000L) else 0L) },
            highlightMonth = 11,
            maxAmount = 1_200_000L,
            onBarTap = {},
        )
    }
}
