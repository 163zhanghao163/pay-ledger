package com.zhaoyingqi.payledger.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zhaoyingqi.payledger.data.MonthSum
import com.zhaoyingqi.payledger.ui.PayLedgerViewModel
import com.zhaoyingqi.payledger.ui.formatCents

/** 统计页：年度柱状图 + 各月合计列表；点柱或点行跳转到对应月份明细。 */
@Composable
fun StatsScreen(vm: PayLedgerViewModel, onJumpToList: () -> Unit) {
    val year by vm.statsYear.collectAsStateWithLifecycle()
    val sums by vm.monthlySums.collectAsStateWithLifecycle()
    val selectedMonth by vm.selectedMonth.collectAsStateWithLifecycle()
    val total = remember(sums) { sums.sumOf { it.totalCents } }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = vm::prevYear) { Text("‹") }
            Text(
                "${year}年支出统计",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = vm::nextYear, enabled = year < vm.latestYear) { Text("›") }
        }
        Text(
            "全年合计  ${formatCents(total)}",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
        BarChart(
            sums = sums,
            highlightMonth = if (selectedMonth.year == year) selectedMonth.monthValue else null,
            onBarTap = { month ->
                vm.pickMonth(java.time.YearMonth.of(year, month))
                onJumpToList()
            },
            modifier = Modifier.fillMaxWidth().height(160.dp).padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(12.dp))
        val byMonth = remember(sums) { sums.associate { it.month to it.totalCents } }
        LazyColumn(Modifier.fillMaxSize()) {
            items(12) { i ->
                val month = i + 1
                val amount = byMonth[month] ?: 0L
                Row(
                    Modifier.fillMaxWidth()
                        .clickable {
                            vm.pickMonth(java.time.YearMonth.of(year, month))
                            onJumpToList()
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${month}月", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    Text(formatCents(amount), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/** 12 根月度柱；超采样点击命中按柱宽计算。 */
@Composable
private fun BarChart(sums: List<MonthSum>, highlightMonth: Int?, onBarTap: (Int) -> Unit, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
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
        (1..12).forEach { month ->
            val total = (byMonth[month] ?: 0L).toFloat()
            val h = if (maxSum > 0) total / maxSum * size.height else 0f
            val x = (month - 1) * (barWidth + gap)
            drawRoundRect(
                color = if (month == highlightMonth) primary else primary.copy(alpha = 0.35f),
                topLeft = Offset(x, size.height - h),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
        }
    }
}

