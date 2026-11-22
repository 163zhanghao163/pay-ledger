package com.zhaoyingqi.payledger.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zhaoyingqi.payledger.data.TransactionRecord
import com.zhaoyingqi.payledger.ui.PayLedgerViewModel
import com.zhaoyingqi.payledger.ui.formatCents
import com.zhaoyingqi.payledger.ui.parseAmountToCents
import java.time.YearMonth

/** 明细页：今日/当月支出卡 + 月份选择 + 按日分组记录列表 + FAB 手动记账。 */
@Composable
fun RecordsScreen(vm: PayLedgerViewModel) {
    val groups by vm.groups.collectAsStateWithLifecycle()
    val todayCents by vm.todayCents.collectAsStateWithLifecycle()
    val monthCents by vm.monthCents.collectAsStateWithLifecycle()
    val selectedMonth by vm.selectedMonth.collectAsStateWithLifecycle()
    val atLatestMonth by vm.atLatestMonth.collectAsStateWithLifecycle()
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
                atLatest = atLatestMonth,
                onPrev = vm::prevMonth,
                onNext = vm::nextMonth,
                onOpenPicker = { showPicker = true },
            )
            if (groups.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("本月暂无记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(bottom = 96.dp)) {
                    groups.forEach { group ->
                        item(key = "header-${group.date.toEpochDay()}") {
                            Text(
                                "${group.label}  支出 ${formatCents(group.totalCents)}",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        items(group.records, key = { it.id }) { record ->
                            RecordRow(record = record, onClick = { editing = record })
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "手动记账")
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
            onSave = { cents, channel, note ->
                vm.addManual(cents, channel, note)
                showAdd = false
            },
        )
    }
    editing?.let { record ->
        EditRecordSheet(
            record = record,
            onDismiss = { editing = null },
            onSave = { cents, note ->
                vm.updateRecord(record.id, cents, note)
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
    atLatest: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpenPicker: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(vertical = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("今日支出", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Text(formatCents(todayCents), style = MaterialTheme.typography.titleLarge)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${month.monthValue}月支出", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Text(formatCents(monthCents), style = MaterialTheme.typography.titleLarge)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onPrev) { Text("‹", style = MaterialTheme.typography.headlineSmall) }
            Text(
                "${month.year}年${month.monthValue}月 ▾",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).clickable(onClick = onOpenPicker).padding(vertical = 8.dp),
            )
            TextButton(onClick = onNext, enabled = !atLatest) {
                Text("›", style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

/** 月份选择弹窗：年份切换 + 12 宫格。 */
@Composable
private fun MonthPickerDialog(
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
                    TextButton(onClick = { viewYear -= 1 }) { Text("‹") }
                    Text(
                        "${viewYear}年",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { viewYear += 1 }, enabled = viewYear < latest.year) { Text("›") }
                }
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..12).chunked(4).forEach { rowMonths ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            rowMonths.forEach { m ->
                                val ym = YearMonth.of(viewYear, m)
                                val selected = ym == current
                                TextButton(
                                    onClick = { onPick(ym) },
                                    enabled = !ym.isAfter(latest),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        "${m}月",
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 手动记账弹窗：金额 + 渠道三选 + 备注。 */
@Composable
private fun ManualAddDialog(onDismiss: () -> Unit, onSave: (Long, String, String) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var channel by remember { mutableStateOf("MANUAL") }
    val cents = parseAmountToCents(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手动记账") },
        text = {
            Column {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("金额（元）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = amount.isNotBlank() && cents == null,
                    supportingText = if (amount.isNotBlank() && cents == null) {
                        { Text("请输入有效金额，如 12.50") }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("WECHAT" to "微信", "ALIPAY" to "支付宝", "MANUAL" to "其他").forEach { (key, label) ->
                        FilterChip(selected = channel == key, onClick = { channel = key }, label = { Text(label) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { cents?.let { onSave(it, channel, note.trim()) } }, enabled = cents != null) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun RecordRow(record: TransactionRecord, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                channelLabel(record.channel),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                record.counterparty.ifEmpty { if (record.note.isEmpty()) record.summary.take(20) else "备注见下" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(formatCents(record.amountCents), style = MaterialTheme.typography.titleMedium)
        }
        if (record.note.isNotEmpty()) {
            Text(
                "备注：${record.note}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun channelLabel(channel: String): String = when (channel) {
    "WECHAT" -> "微信"
    "ALIPAY" -> "支付宝"
    else -> "其他"
}

/** 编辑记录：金额、备注可改，删除入口显式化。 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun EditRecordSheet(
    record: TransactionRecord,
    onDismiss: () -> Unit,
    onSave: (Long, String) -> Unit,
    onDelete: () -> Unit,
) {
    var amount by remember(record.id) { mutableStateOf(formatCents(record.amountCents).removePrefix("¥")) }
    var note by remember(record.id) { mutableStateOf(record.note) }
    val cents = parseAmountToCents(amount)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp)) {
            Text("编辑记录", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("金额（元）") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                isError = amount.isNotBlank() && cents == null,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("备注") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("删除这条记录") }
                Row {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { cents?.let { onSave(it, note.trim()) } }, enabled = cents != null) { Text("保存") }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
