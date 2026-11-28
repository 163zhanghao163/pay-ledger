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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zhaoyingqi.payledger.data.CategoryRule
import com.zhaoyingqi.payledger.data.TransactionRecord
import com.zhaoyingqi.payledger.ui.formatDate
import com.zhaoyingqi.payledger.ui.formatLocalTime
import com.zhaoyingqi.payledger.ui.localDateToPickerMillis
import com.zhaoyingqi.payledger.ui.pickerMillisToLocalDate
import java.time.LocalDate
import java.time.LocalTime

/** 金额输入框（新增与编辑共用）。 */
@Composable
internal fun AmountField(
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

/** 单选选项条：新增/编辑弹窗的渠道与类型选择共用。 */
@Composable
internal fun OptionChipRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    accentOf: @Composable (String) -> Color,
    onSelect: (String) -> Unit,
) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (key, text) ->
                val isSelected = selected == key
                val accent = accentOf(key)
                val shape = RoundedCornerShape(12.dp)
                Surface(
                    modifier = Modifier.weight(1f).height(42.dp)
                        .clip(shape)
                        .clickable { onSelect(key) },
                    shape = shape,
                    color = if (isSelected) {
                        accent.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) accent.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
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
                        Text(text, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/** 渠道选择：微信 / 支付宝 / 其他。 */
@Composable
internal fun ChannelPicker(channel: String, onChannelChange: (String) -> Unit) {
    OptionChipRow(
        label = "渠道",
        options = listOf("WECHAT" to "微信", "ALIPAY" to "支付宝", "MANUAL" to "其他"),
        selected = channel,
        accentOf = { channelAccent(it) },
        onSelect = onChannelChange,
    )
}

/** 类型选择：支出 / 收入（v07 6.4）。 */
@Composable
internal fun TypePicker(type: String, onTypeChange: (String) -> Unit) {
    OptionChipRow(
        label = "类型",
        options = listOf(
            TransactionRecord.TYPE_EXPENSE to "支出",
            TransactionRecord.TYPE_INCOME to "收入",
        ),
        selected = type,
        accentOf = { if (it == TransactionRecord.TYPE_INCOME) MaterialTheme.colorScheme.tertiary else channelAccent(it) },
        onSelect = onTypeChange,
    )
}

/** 分类选择（v07 5.2）：从内置分类名里选，可清空为「不分类」。 */
@Composable
internal fun CategoryPicker(category: String?, onCategoryChange: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(
            "分类",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(5.dp))
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(13.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(category ?: "不分类", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "选择分类",
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("不分类") },
                    onClick = {
                        onCategoryChange(null)
                        expanded = false
                    },
                )
                CategoryRule.CATEGORY_LABELS.forEach { label ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onCategoryChange(label)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/** 日期 + 时间选择行（手动记账与编辑共用）。 */
@Composable
internal fun DateTimeRow(
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
internal fun DateSelectDialog(
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
internal fun TimeSelectDialog(
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
