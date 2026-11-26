package com.zhaoyingqi.payledger.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zhaoyingqi.payledger.data.TransactionRecord
import com.zhaoyingqi.payledger.ui.PayLedgerViewModel
import com.zhaoyingqi.payledger.ui.formatClock
import com.zhaoyingqi.payledger.ui.formatCents
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** 明细页：今日/当月支出卡 + 月份选择 + 按日分组记录列表 + FAB 手动记账。 */
@Composable
fun RecordsScreen(vm: PayLedgerViewModel) {
    val groups by vm.groups.collectAsStateWithLifecycle()
    val todayCents by vm.todayCents.collectAsStateWithLifecycle()
    val monthCents by vm.monthCents.collectAsStateWithLifecycle()
    val selectedMonth by vm.selectedMonth.collectAsStateWithLifecycle()
    val atLatestMonth by vm.atLatestMonth.collectAsStateWithLifecycle()
    val recordCount = remember(groups) { groups.sumOf { it.records.size } }
    var editing by remember { mutableStateOf<TransactionRecord?>(null) }
    var deleting by remember { mutableStateOf<TransactionRecord?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            SummaryCard(
                todayCents = todayCents,
                monthCents = monthCents,
                month = selectedMonth,
                recordCount = recordCount,
                atLatest = atLatestMonth,
                onPrev = vm::prevMonth,
                onNext = vm::nextMonth,
                onOpenPicker = { showPicker = true },
            )
            if (groups.isEmpty()) {
                EmptyState(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    onAdd = { showAdd = true },
                )
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 104.dp),
                ) {
                    groups.forEach { group ->
                        item(key = "header-${group.date.toEpochDay()}") {
                            DayHeader(group.label, group.records.size, group.totalCents)
                        }
                        items(group.records, key = { it.id }) { record ->
                            RecordCard(
                                record = record,
                                onClick = { editing = record },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
        if (groups.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("记一笔") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
                shape = RoundedCornerShape(18.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }

    if (showPicker) {
        MonthPickerDialog(
            current = selectedMonth,
            latest = vm.latestMonth,
            onPick = {
                vm.pickMonth(it)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
    if (showAdd) {
        ManualAddDialog(
            onDismiss = { showAdd = false },
            onSave = { cents, channel, note, occurredAt ->
                vm.addManual(cents, channel, note, occurredAt)
                vm.pickMonth(monthOf(occurredAt))
                showAdd = false
            },
        )
    }
    editing?.let { record ->
        EditRecordSheet(
            record = record,
            onDismiss = { editing = null },
            onSave = { cents, note, occurredAt ->
                vm.updateRecord(record.id, cents, note, occurredAt)
                vm.pickMonth(monthOf(occurredAt))
                editing = null
            },
            onDelete = {
                deleting = record
                editing = null
            },
        )
    }
    deleting?.let { record ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除记录") },
            text = { Text("删除 ${formatCents(record.amountCents)} 这条记录？") },
            confirmButton = {
                TextButton(onClick = { vm.delete(record.id); deleting = null }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun SummaryCard(
    todayCents: Long,
    monthCents: Long,
    month: YearMonth,
    recordCount: Int,
    atLatest: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpenPicker: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.extraLarge
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        shape = shape,
        color = scheme.primary,
        contentColor = scheme.onPrimary,
        shadowElevation = 6.dp,
    ) {
        Box(Modifier.clip(shape)) {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(
                    color = scheme.onPrimary.copy(alpha = 0.09f),
                    radius = size.minDimension * 0.48f,
                    center = Offset(size.width * 0.94f, 0f),
                )
                drawCircle(
                    color = scheme.onPrimary.copy(alpha = 0.06f),
                    radius = size.minDimension * 0.32f,
                    center = Offset(size.width * 0.05f, size.height * 1.02f),
                )
            }
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (recordCount > 0) {
                                "${month.monthValue}月支出 · ${recordCount}笔"
                            } else {
                                "${month.monthValue}月支出"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.onPrimary.copy(alpha = 0.78f),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            formatCents(monthCents),
                            style = MaterialTheme.typography.displaySmall,
                            color = scheme.onPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = scheme.onPrimary.copy(alpha = 0.15f),
                    ) {
                        Column(
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.End,
                        ) {
                            Text(
                                "今日",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onPrimary.copy(alpha = 0.75f),
                            )
                            Text(
                                formatCents(todayCents),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MonthArrowButton(
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        description = "上个月",
                        enabled = true,
                        onClick = onPrev,
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        color = scheme.onPrimary.copy(alpha = 0.14f),
                    ) {
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable(onClick = onOpenPicker)
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${month.year}年${month.monthValue}月",
                                style = MaterialTheme.typography.titleSmall,
                                color = scheme.onPrimary,
                            )
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = "选择月份",
                                tint = scheme.onPrimary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    MonthArrowButton(
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        description = "下个月",
                        enabled = !atLatest,
                        onClick = onNext,
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthArrowButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = if (enabled) 0.14f else 0.07f),
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(42.dp)) {
            Icon(
                icon,
                contentDescription = description,
                tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = if (enabled) 0.95f else 0.35f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun DayHeader(label: String, count: Int, totalCents: Long) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                "$count 笔 · ${formatCents(totalCents)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun RecordCard(
    record: TransactionRecord,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val title = recordTitle(record)
    val note = record.note.trim()
    val subtitle = if (note.isNotEmpty() && note != title) {
        "${formatClock(record.occurredAt)} · $note"
    } else {
        formatClock(record.occurredAt)
    }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = scheme.surfaceContainerLow,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelBadge(record.channel)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                formatCents(record.amountCents),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier, onAdd: () -> Unit) {
    Column(
        modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(Modifier.size(84.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "¥",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("本月还没有记录", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "自动记账已就绪，也可以手动补一笔",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onAdd,
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("手动记一笔")
        }
    }
}

private fun recordTitle(record: TransactionRecord): String =
    record.counterparty.ifBlank {
        record.note.trim().ifBlank {
            record.summary.ifBlank { "手动记账" }
        }
    }

/** 记录所属月份（用于保存后自动切换到对应月份）。 */
private fun monthOf(epochMillis: Long): YearMonth =
    YearMonth.from(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
