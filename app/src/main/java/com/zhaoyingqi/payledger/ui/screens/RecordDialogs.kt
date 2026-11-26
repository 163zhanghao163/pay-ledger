package com.zhaoyingqi.payledger.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zhaoyingqi.payledger.data.TransactionRecord
import com.zhaoyingqi.payledger.ui.epochMillisToLocalDateTime
import com.zhaoyingqi.payledger.ui.formatCents
import com.zhaoyingqi.payledger.ui.formatDate
import com.zhaoyingqi.payledger.ui.formatLocalTime
import com.zhaoyingqi.payledger.ui.localDateToPickerMillis
import com.zhaoyingqi.payledger.ui.localDateTimeToEpochMillis
import com.zhaoyingqi.payledger.ui.parseAmountToCents
import com.zhaoyingqi.payledger.ui.pickerMillisToLocalDate
import java.time.LocalDate
import java.time.LocalTime

/** 手动记账弹窗：金额 + 渠道三选 + 日期/时间 + 备注。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManualAddDialog(
    onDismiss: () -> Unit,
    onSave: (Long, String, String, Long) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var channel by remember { mutableStateOf("MANUAL") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var time by remember { mutableStateOf(LocalTime.now()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val cents = parseAmountToCents(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "¥",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("手动记账", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "补记一笔支出",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            Column {
                AmountField(amount = amount, onValueChange = { amount = it }, cents = cents)
                Spacer(Modifier.height(10.dp))
                ChannelPicker(channel = channel, onChannelChange = { channel = it })
                Spacer(Modifier.height(10.dp))
                DateTimeRow(
                    date = date,
                    time = time,
                    onPickDate = { showDatePicker = true },
                    onPickTime = { showTimePicker = true },
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    cents?.let { onSave(it, channel, note.trim(), localDateTimeToEpochMillis(date, time)) }
                },
                enabled = cents != null,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
    if (showDatePicker) {
        DateSelectDialog(
            initial = date,
            onConfirm = {
                date = it
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
    if (showTimePicker) {
        TimeSelectDialog(
            initial = time,
            onConfirm = {
                time = it
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }
}

@Composable
private fun AmountField(
    amount: String,
    onValueChange: (String) -> Unit,
    cents: Long?,
) {
    OutlinedTextField(
        value = amount,
        onValueChange = { if (it.length <= 12) onValueChange(it) },
        label = { Text("金额") },
        placeholder = { Text("0.00") },
        leadingIcon = {
            Text(
                "¥",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        isError = amount.isNotBlank() && cents == null,
        supportingText = if (amount.isNotBlank() && cents == null) {
            { Text("请输入有效金额，如 12.50") }
        } else {
            null
        },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ChannelPicker(channel: String, onChannelChange: (String) -> Unit) {
    val options = listOf("WECHAT" to "微信", "ALIPAY" to "支付宝", "MANUAL" to "其他")
    Column {
        Text(
            "渠道",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (key, label) ->
                val selected = channel == key
                val accent = channelAccent(key)
                val shape = RoundedCornerShape(12.dp)
                Surface(
                    modifier = Modifier.weight(1f).height(42.dp)
                        .clip(shape)
                        .clickable { onChannelChange(key) },
                    shape = shape,
                    color = if (selected) {
                        accent.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    contentColor = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    border = BorderStroke(
                        1.dp,
                        if (selected) accent.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    ),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(8.dp)
                                .clip(CircleShape)
                                .background(accent),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(label, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/** 日期 + 时间选择行（手动记账与编辑共用）。 */
@Composable
private fun DateTimeRow(
    date: LocalDate,
    time: LocalTime,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
) {
    Column {
        Text(
            "记账时间",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onPickDate,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(13.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
            ) {
                Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(formatDate(date), style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(
                onClick = onPickTime,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(13.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
            ) {
                Text(formatLocalTime(time), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateSelectDialog(
    initial: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = localDateToPickerMillis(initial))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let {
                        onConfirm(minOf(pickerMillisToLocalDate(it), LocalDate.now()))
                    }
                },
                enabled = state.selectedDateMillis != null,
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeSelectDialog(
    initial: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择时间") },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 编辑记录：金额、日期时间、备注可改，删除入口显式化。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditRecordSheet(
    record: TransactionRecord,
    onDismiss: () -> Unit,
    onSave: (Long, String, Long) -> Unit,
    onDelete: () -> Unit,
) {
    val initial = remember(record.id) { epochMillisToLocalDateTime(record.occurredAt) }
    var amount by remember(record.id) { mutableStateOf(formatCents(record.amountCents).removePrefix("¥")) }
    var note by remember(record.id) { mutableStateOf(record.note) }
    var date by remember(record.id) { mutableStateOf(initial.toLocalDate()) }
    var time by remember(record.id) { mutableStateOf(initial.toLocalTime()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val cents = parseAmountToCents(amount)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChannelBadge(record.channel, 38.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("编辑记录", style = MaterialTheme.typography.titleLarge)
                    Text(
                        record.summary.ifBlank { "支出记录" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            AmountField(amount = amount, onValueChange = { amount = it }, cents = cents)
            Spacer(Modifier.height(10.dp))
            DateTimeRow(
                date = date,
                time = time,
                onPickDate = { showDatePicker = true },
                onPickTime = { showTimePicker = true },
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("备注") },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("删除这条记录") }
                Row {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(6.dp))
                    Button(
                        onClick = {
                            cents?.let { onSave(it, note.trim(), localDateTimeToEpochMillis(date, time)) }
                        },
                        enabled = cents != null,
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("保存") }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (showDatePicker) {
        DateSelectDialog(
            initial = date,
            onConfirm = {
                date = it
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
    if (showTimePicker) {
        TimeSelectDialog(
            initial = time,
            onConfirm = {
                time = it
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }
}
