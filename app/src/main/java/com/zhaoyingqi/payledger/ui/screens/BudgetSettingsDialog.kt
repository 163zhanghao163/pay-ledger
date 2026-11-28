package com.zhaoyingqi.payledger.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zhaoyingqi.payledger.budget.BudgetSettings
import com.zhaoyingqi.payledger.budget.BudgetType
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 预算设置弹层（v08 4.4/D3）：类型三选一，每日 / 每月金额分开保存互不覆盖；
 * 金额非负、精确到分（允许 0 表示该项未设置，对应类型按未启用处理）。
 */
@Composable
internal fun BudgetSettingsDialog(
    current: BudgetSettings,
    onDismiss: () -> Unit,
    onSave: (BudgetSettings) -> Unit,
) {
    var type by remember { mutableStateOf(current.type) }
    var dailyInput by remember { mutableStateOf(centsToInput(current.dailyCents)) }
    var monthlyInput by remember { mutableStateOf(centsToInput(current.monthlyCents)) }

    val dailyCents = parseBudgetCents(dailyInput)
    val monthlyCents = parseBudgetCents(monthlyInput)
    val dailyInvalid = dailyInput.isNotBlank() && dailyCents == null
    val monthlyInvalid = monthlyInput.isNotBlank() && monthlyCents == null
    val canSave = !dailyInvalid && !monthlyInvalid && (type == BudgetType.OFF || when (type) {
        BudgetType.DAILY -> dailyCents != null && dailyInput.isNotBlank()
        BudgetType.MONTHLY -> monthlyCents != null && monthlyInput.isNotBlank()
        BudgetType.OFF -> true
    })

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("预算提醒") },
        text = {
            Column {
                OptionChipRow(
                    label = "类型",
                    options = listOf(
                        BudgetType.OFF.name to "关闭",
                        BudgetType.DAILY.name to "每日",
                        BudgetType.MONTHLY.name to "每月",
                    ),
                    selected = type.name,
                    accentOf = { MaterialTheme.colorScheme.primary },
                    onSelect = { key ->
                        type = runCatching { BudgetType.valueOf(key) }.getOrDefault(BudgetType.OFF)
                    },
                )
                if (type == BudgetType.DAILY) {
                    BudgetAmountField(
                        label = "每日预算（元）",
                        value = dailyInput,
                        isError = dailyInvalid,
                        onValueChange = { dailyInput = it },
                    )
                    Text(
                        "当天支出达到该额度时提醒一次，次日恢复",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (type == BudgetType.MONTHLY) {
                    BudgetAmountField(
                        label = "每月预算（元）",
                        value = monthlyInput,
                        isError = monthlyInvalid,
                        onValueChange = { monthlyInput = it },
                    )
                    Text(
                        "月支出达到 80% 与 100% 各提醒一次，下月自动重置",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (type == BudgetType.OFF) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "关闭后不再显示预算进度，也不会提醒",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = canSave, onClick = {
                onSave(
                    BudgetSettings(
                        type = type,
                        dailyCents = dailyCents ?: 0L,
                        monthlyCents = monthlyCents ?: 0L,
                    ),
                )
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun BudgetAmountField(
    label: String,
    value: String,
    isError: Boolean,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 12) onValueChange(it) },
        label = { Text(label) },
        placeholder = { Text("0.00") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        isError = isError,
        supportingText = if (isError) {
            { Text("请输入非负金额，最多两位小数") }
        } else {
            null
        },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 分 → 输入框文本（0 显示空，表示未设置）。 */
private fun centsToInput(cents: Long): String =
    if (cents <= 0) "" else BigDecimal(cents).movePointLeft(2).setScale(2, RoundingMode.HALF_UP).toPlainString()

/** 预算金额输入（元）→ 分：非负、最多两位小数；0 合法表示未设置；非法返回 null。 */
internal fun parseBudgetCents(input: String): Long? {
    val s = input.trim().removePrefix("¥").removePrefix("￥").trim()
    if (s.isEmpty()) return null
    val m = Regex("""(\d{1,8})(\.\d{1,2})?""").matchEntire(s) ?: return null
    val yuan = m.groupValues[1].toLong()
    val cents = m.groupValues[2].removePrefix(".").padEnd(2, '0').ifEmpty { "00" }.toLong()
    return yuan * 100 + cents
}
