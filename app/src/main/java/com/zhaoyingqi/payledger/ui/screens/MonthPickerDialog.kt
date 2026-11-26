package com.zhaoyingqi.payledger.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.YearMonth

/** 月份选择弹窗：年份切换 + 12 宫格。 */
@Composable
internal fun MonthPickerDialog(
    current: YearMonth,
    latest: YearMonth,
    onPick: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
) {
    var viewYear by remember(current) { mutableStateOf(current.year) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择月份") },
        text = {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewYear -= 1 }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一年")
                    }
                    Text(
                        "${viewYear}年",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { viewYear += 1 },
                        enabled = viewYear < latest.year,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一年")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..12).chunked(4).forEach { rowMonths ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowMonths.forEach { month ->
                                val ym = YearMonth.of(viewYear, month)
                                val enabled = !ym.isAfter(latest)
                                val selected = ym == current
                                val cellShape = RoundedCornerShape(12.dp)
                                Surface(
                                    modifier = Modifier.weight(1f).height(44.dp)
                                        .clip(cellShape)
                                        .clickable(enabled = enabled) { onPick(ym) },
                                    shape = cellShape,
                                    color = when {
                                        selected -> MaterialTheme.colorScheme.primary
                                        enabled -> MaterialTheme.colorScheme.surfaceContainerHigh
                                        else -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.35f)
                                    },
                                    contentColor = when {
                                        selected -> MaterialTheme.colorScheme.onPrimary
                                        enabled -> MaterialTheme.colorScheme.onSurface
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    },
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("${month}月", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
