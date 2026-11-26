package com.zhaoyingqi.payledger.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zhaoyingqi.payledger.data.MonthSum
import com.zhaoyingqi.payledger.ui.PayLedgerViewModel
import com.zhaoyingqi.payledger.ui.formatCents
import java.time.YearMonth

/** 统计页：年度概览 + 月度柱状图 + 各月进度列表；点柱或点行跳转到对应月份明细。 */
@Composable
fun StatsScreen(vm: PayLedgerViewModel, onJumpToList: () -> Unit) {
    val year by vm.statsYear.collectAsStateWithLifecycle()
    val sums by vm.monthlySums.collectAsStateWithLifecycle()
    val selectedMonth by vm.selectedMonth.collectAsStateWithLifecycle()
    val total = remember(sums) { sums.sumOf { it.totalCents } }
    val activeMonths = remember(sums) { sums.count { it.totalCents > 0 } }
    val average = if (activeMonths > 0) total / activeMonths else 0L
    val peak = remember(sums) { sums.maxByOrNull { it.totalCents } }
    val byMonth = remember(sums) { sums.associate { it.month to it.totalCents } }
    val maxAmount = remember(sums) { sums.maxOfOrNull { it.totalCents } ?: 0L }
    val highlightMonth = if (selectedMonth.year == year) selectedMonth.monthValue else null
    val openMonth: (Int) -> Unit = { month ->
        vm.pickMonth(YearMonth.of(year, month))
        onJumpToList()
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("支出统计", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "按年查看支出走势",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            YearSwitcher(
                year = year,
                canNext = year < vm.latestYear,
                onPrev = vm::prevYear,
                onNext = vm::nextYear,
            )
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OverviewCard(
                    year = year,
                    total = total,
                    average = average,
                    peak = peak,
                )
            }
            item {
                TrendCard(
                    sums = sums,
                    highlightMonth = highlightMonth,
                    maxAmount = maxAmount,
                    onBarTap = openMonth,
                )
            }
            item {
                MonthlyBreakdownCard(
                    byMonth = byMonth,
                    maxAmount = maxAmount,
                    highlightMonth = highlightMonth,
                    onPick = openMonth,
                )
            }
        }
    }
}

@Composable
private fun YearSwitcher(
    year: Int,
    canNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev, modifier = Modifier.size(38.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一年", modifier = Modifier.size(20.dp))
            }
            Text(
                "$year 年",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
            IconButton(
                onClick = onNext,
                enabled = canNext,
                modifier = Modifier.size(38.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一年", modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun OverviewCard(
    year: Int,
    total: Long,
    average: Long,
    peak: MonthSum?,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.extraLarge
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = scheme.primaryContainer,
        contentColor = scheme.onPrimaryContainer,
        shadowElevation = 2.dp,
    ) {
        Box(Modifier.clip(shape)) {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(
                    color = scheme.onPrimaryContainer.copy(alpha = 0.06f),
                    radius = size.minDimension * 0.5f,
                    center = Offset(size.width * 0.98f, 0f),
                )
            }
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "全年支出",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onPrimaryContainer.copy(alpha = 0.75f),
                    )
                    Spacer(Modifier.weight(1f))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = scheme.onPrimaryContainer.copy(alpha = 0.08f),
                    ) {
                        Text(
                            "$year",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    formatCents(total),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OverviewStat("月均", formatCents(average), Modifier.weight(1f))
                    OverviewStat(
                        "最高",
                        peak?.let { "${it.month}月 · ${formatCents(it.totalCents)}" } ?: "暂无",
                        Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TrendCard(
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
            BarChart(
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
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** 12 根月度柱；带背景槽与高亮月，点击命中按柱宽计算。 */
@Composable
private fun BarChart(
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

    Canvas(
        modifier.pointerInput(sums) {
            detectTapGestures { pos ->
                val gap = barGap.toPx()
                val barWidth = (size.width - gap * 11) / 12
                val index = (pos.x / (barWidth + gap)).toInt().coerceIn(0, 11)
                onBarTap(index + 1)
            }
        },
    ) {
        val gap = barGap.toPx()
        val barWidth = (size.width - gap * 11) / 12
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
}

@Composable
private fun MonthlyBreakdownCard(
    byMonth: Map<Int, Long>,
    maxAmount: Long,
    highlightMonth: Int?,
    onPick: (Int) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("各月明细", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    "点击跳转",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                Modifier.padding(horizontal = 8.dp).padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                (1..12).forEach { month ->
                    val amount = byMonth[month] ?: 0L
                    val selected = highlightMonth == month
                    val fraction = if (maxAmount > 0) amount.toFloat() / maxAmount else 0f
                    val rowShape = RoundedCornerShape(14.dp)
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(rowShape)
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                } else {
                                    Color.Transparent
                                },
                            )
                            .clickable { onPick(month) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${month}月",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    formatCents(amount),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (amount > 0) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (amount > 0) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Box(
                                Modifier.fillMaxWidth().height(4.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            ) {
                                Box(
                                    Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f))
                                        .fillMaxHeight()
                                        .clip(CircleShape)
                                        .background(
                                            if (selected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                            },
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
