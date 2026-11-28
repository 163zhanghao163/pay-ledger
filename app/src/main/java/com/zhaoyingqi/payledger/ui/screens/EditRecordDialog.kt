package com.zhaoyingqi.payledger.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zhaoyingqi.payledger.data.TransactionRecord
import com.zhaoyingqi.payledger.ui.epochMillisToLocalDateTime
import com.zhaoyingqi.payledger.ui.formatCents
import com.zhaoyingqi.payledger.ui.localDateTimeToEpochMillis
import com.zhaoyingqi.payledger.ui.parseAmountToCents
import java.time.LocalDate
import java.time.LocalTime

/**
 * 编辑记录（v07 3.2/D4）：金额、时间、备注、渠道、商户、类型、分类都可改，
 * 保存后 timeEstimated 清零；source 与 dedupKey 不在更新列，结构上保证不被改。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditRecordSheet(
    record: TransactionRecord,
    onDismiss: () -> Unit,
    onSave: (id: Long, cents: Long, note: String, occurredAt: Long, channel: String, counterparty: String, type: String, category: String?) -> Unit,
    onDelete: () -> Unit,
) {
    val initial = remember(record.id) { epochMillisToLocalDateTime(record.occurredAt) }
    var amount by remember(record.id) { mutableStateOf(formatCents(record.amountCents).removePrefix("¥")) }
    var note by remember(record.id) { mutableStateOf(record.note) }
    var channel by remember(record.id) { mutableStateOf(record.channel) }
    var counterparty by remember(record.id) { mutableStateOf(record.counterparty) }
    var type by remember(record.id) { mutableStateOf(record.type) }
    var category by remember(record.id) { mutableStateOf(record.category) }
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
            ChannelPicker(channel = channel, onChannelChange = { channel = it })
            Spacer(Modifier.height(10.dp))
            TypePicker(type = type, onTypeChange = { type = it })
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = counterparty,
                onValueChange = { if (it.length <= 30) counterparty = it },
                label = { Text("商户") },
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            CategoryPicker(category = category, onCategoryChange = { category = it })
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
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
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
                            cents?.let {
                                onSave(
                                    record.id,
                                    it,
                                    note.trim(),
                                    localDateTimeToEpochMillis(date, time),
                                    channel,
                                    counterparty.trim(),
                                    type,
                                    category,
                                )
                            }
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
