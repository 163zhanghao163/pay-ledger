package com.zhaoyingqi.payledger.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zhaoyingqi.payledger.data.CategoryMatcher
import com.zhaoyingqi.payledger.data.CategoryRule
import com.zhaoyingqi.payledger.data.TransactionRecord
import com.zhaoyingqi.payledger.ui.localDateTimeToEpochMillis
import com.zhaoyingqi.payledger.ui.parseAmountToCents
import java.time.LocalDate
import java.time.LocalTime

/**
 * 手动记账弹窗：金额 + 渠道 + 类型（记收入开启后可选）+ 商户 + 分类 + 日期/时间 + 备注。
 * 分类默认值由 [CategoryMatcher] 按商户名匹配（v07 5.2），用户可改；无匹配或用户改动后不追溯。
 */
@Composable
internal fun ManualAddDialog(
    incomeEnabled: Boolean,
    rules: List<CategoryRule>,
    onDismiss: () -> Unit,
    onSave: (cents: Long, channel: String, note: String, occurredAt: Long, counterparty: String, type: String, category: String?) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var channel by remember { mutableStateOf("MANUAL") }
    var counterparty by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TransactionRecord.TYPE_EXPENSE) }
    // null 表示「跟随匹配结果」；用户在弹层里手动选过分类后不再跟随。
    var categoryOverride by remember { mutableStateOf<String?>(null) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var time by remember { mutableStateOf(LocalTime.now()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val cents = parseAmountToCents(amount)
    val matchedCategory = remember(counterparty, rules) { CategoryMatcher.match(counterparty.trim(), rules) }
    val effectiveCategory = categoryOverride ?: matchedCategory
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
                if (incomeEnabled) {
                    Spacer(Modifier.height(10.dp))
                    TypePicker(type = type, onTypeChange = { type = it })
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = counterparty,
                    onValueChange = { if (it.length <= 30) counterparty = it },
                    label = { Text("商户（可选）") },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                CategoryPicker(category = effectiveCategory, onCategoryChange = { categoryOverride = it })
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
                    cents?.let {
                        onSave(
                            it,
                            channel,
                            note.trim(),
                            localDateTimeToEpochMillis(date, time),
                            counterparty.trim(),
                            type,
                            effectiveCategory,
                        )
                    }
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
