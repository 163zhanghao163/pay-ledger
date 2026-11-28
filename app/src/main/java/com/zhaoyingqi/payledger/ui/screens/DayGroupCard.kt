package com.zhaoyingqi.payledger.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zhaoyingqi.payledger.data.TransactionRecord
import com.zhaoyingqi.payledger.ui.formatClock
import com.zhaoyingqi.payledger.ui.formatCents

// —— 明细页按日分组卡片与空态（v08 5.1 拆分自 RecordsScreen.kt）——

@Composable
internal fun DayHeader(label: String, count: Int, totalCents: Long) {
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
internal fun RecordCard(
    record: TransactionRecord,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val isIncome = record.type == TransactionRecord.TYPE_INCOME
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isIncome) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = scheme.tertiaryContainer,
                        ) {
                            Text(
                                "收入",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (record.category != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            record.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.primary,
                        )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                if (isIncome) "+${formatCents(record.amountCents)}" else formatCents(record.amountCents),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isIncome) scheme.tertiary else scheme.onSurface,
            )
        }
    }
}
@Composable
internal fun EmptyState(modifier: Modifier = Modifier, onAdd: () -> Unit) {
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
/**
 * 空库引导（v08 3.3/I-3.5）：无记录且已授权时显示，给三路状态检查清单；
 * 任一路未就绪给出对应跳转提示，全部就绪则引导记一笔验证。
 */
@Composable
internal fun OnboardingEmptyState(
    authorized: Boolean,
    notificationConnected: Boolean,
    accessibilityConnected: Boolean,
    modifier: Modifier = Modifier,
    onAdd: () -> Unit,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier.padding(horizontal = 24.dp),
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
        Spacer(Modifier.height(14.dp))
        Text("付款自动记账已就绪？", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "完成下面三步后，付款即可自动入账",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        ChecklistRow("1. 系统已授予「通知使用权」", authorized, scheme)
        ChecklistRow("2. 通知监听服务已连接", notificationConnected, scheme)
        ChecklistRow("3. 无障碍兜底已开启（推荐）", accessibilityConnected, scheme)
        Spacer(Modifier.height(8.dp))
        if (!notificationConnected || !authorized) {
            Text(
                "到「帮助」页按步骤开启；部分品牌需允许自启动与后台运行",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.error,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(14.dp))
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
    // 任一路未就绪时提供直达设置入口
    if (!authorized || !notificationConnected || !accessibilityConnected) {
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) {
            Text("打开设置")
        }
    }
}

@Composable
private fun ChecklistRow(label: String, ok: Boolean, scheme: androidx.compose.material3.ColorScheme) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (ok) "✓" else "○",
            style = MaterialTheme.typography.titleMedium,
            color = if (ok) scheme.primary else scheme.onSurfaceVariant,
            modifier = Modifier.width(26.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (ok) scheme.onSurface else scheme.onSurfaceVariant,
        )
    }
}
private fun recordTitle(record: TransactionRecord): String =
    record.counterparty.ifBlank {
        record.note.trim().ifBlank {
            record.summary.ifBlank { "手动记账" }
        }
    }

// —— 预览（v08 5.2，亮/暗）——

private fun previewRecord(type: String, category: String? = null) = TransactionRecord(
    id = 1L, amountCents = 23_50L, channel = "WECHAT", counterparty = "星巴克",
    occurredAt = System.currentTimeMillis(), summary = "微信支付 23.50 元", createdAt = System.currentTimeMillis(),
    type = type, category = category, source = "notification", dedupKey = "preview",
)

@Preview(name = "记录卡（亮）", showBackground = true)
@Composable
private fun RecordCardLightPreview() {
    MaterialTheme {
        Column {
            RecordCard(previewRecord("EXPENSE", "餐饮"), onClick = {}, modifier = Modifier.padding(4.dp))
            RecordCard(previewRecord("INCOME"), onClick = {}, modifier = Modifier.padding(4.dp))
        }
    }
}

@Preview(name = "记录卡（暗）", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RecordCardDarkPreview() {
    MaterialTheme { RecordCard(previewRecord("EXPENSE", "餐饮"), onClick = {}) }
}

@Preview(name = "空态（亮）", showBackground = true, heightDp = 320)
@Composable
private fun EmptyStateLightPreview() {
    MaterialTheme { Box(Modifier.fillMaxWidth()) { EmptyState(onAdd = {}) } }
}

@Preview(name = "空库引导（暗）", showBackground = true, heightDp = 420, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OnboardingDarkPreview() {
    MaterialTheme {
        Box(Modifier.fillMaxWidth()) {
            OnboardingEmptyState(
                authorized = true,
                notificationConnected = true,
                accessibilityConnected = false,
                onAdd = {},
            )
        }
    }
}
