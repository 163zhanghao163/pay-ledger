package com.zhaoyingqi.payledger.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhaoyingqi.payledger.PayLedgerApplication
import com.zhaoyingqi.payledger.notification.ListenerHealth
import com.zhaoyingqi.payledger.notification.NotificationLedgerService
import com.zhaoyingqi.payledger.notification.NotificationParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 双通道状态行：通知监听连接状态 + 无障碍开关状态；未连接时提供重连。 */
@Composable
private fun ChannelStatusRow(
    listenerConnected: Boolean,
    lastConnectedText: String?,
    accessibilityEnabled: Boolean,
    onRebind: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (listenerConnected) "通知监听：已连接 ✓" else "通知监听：未连接 ✗",
                style = MaterialTheme.typography.bodySmall,
                color = if (listenerConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.padding(4.dp))
            Text(
                if (accessibilityEnabled) "无障碍：已开启 ✓" else "无障碍：未开启（可选）",
                style = MaterialTheme.typography.bodySmall,
                color = if (accessibilityEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!listenerConnected) {
                Spacer(Modifier.weight(1f))
                Button(onClick = onRebind) { Text("重连") }
            }
        }
        if (!listenerConnected && lastConnectedText != null) {
            Text(
                "上次连接：$lastConnectedText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 仅 debug 构建可达（顶栏「调试」进入）：查看微信/支付宝最近通知 + 模拟付款注入。 */
@Composable
fun DebugPreviewScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as PayLedgerApplication
    var refreshTick by remember { mutableIntStateOf(0) }
    val items = remember(refreshTick) { app.recentNotifications.snapshot() }
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val scope = rememberCoroutineScope()
    val listenerConnected = remember(refreshTick) { NotificationLedgerService.connected }
    val lastConnectedText = remember(refreshTick) {
        ListenerHealth.lastConnectedAt(context).takeIf { it > 0 }?.let { timeFmt.format(Date(it)) }
    }
    val accessibilityEnabled = remember(refreshTick) { checkAccessibility(context) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("返回") }
            Text("最近通知（调试）", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { refreshTick += 1 }) { Text("刷新") }
            TextButton(onClick = {
                app.recentNotifications.clear()
                refreshTick += 1
            }) { Text("清空") }
        }
        Text(
            "说明：此页只展示微信/支付宝最近的通知，用于校准解析规则；其他应用的通知不会进入缓冲。" +
                "无障碍通道在系统层只监听微信/支付宝两个应用。数据仅存内存（最多 30 条、保留 7 天），不占存储。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        // 状态行每 3 秒自动刷新（重连后需要片刻才生效）
        LaunchedEffect(Unit) {
            while (isActive) {
                delay(3_000)
                refreshTick += 1
            }
        }
        ChannelStatusRow(
            listenerConnected = listenerConnected,
            lastConnectedText = lastConnectedText,
            accessibilityEnabled = accessibilityEnabled,
            onRebind = {
                // 逐级重连（requestRebind → 必要时重置监听组件），2 秒后复查状态
                ListenerHealth.reconnect(context, app.applicationScope)
                scope.launch {
                    delay(2_000)
                    refreshTick += 1
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                scope.launch {
                    NotificationParser.parse(
                        "com.tencent.mm",
                        "微信",
                        "微信支付凭证\n付款成功 ¥9.99\n付款给 测试商户",
                    )?.let { app.savePayment(it, System.currentTimeMillis()) }
                }
            }) { Text("模拟微信付款") }
            Spacer(Modifier.padding(4.dp))
            OutlinedButton(onClick = {
                scope.launch {
                    NotificationParser.parse(
                        "com.eg.android.AlipayGphone",
                        "支付宝通知",
                        "付款成功 ¥8.88 向 测试商户",
                    )?.let { app.savePayment(it, System.currentTimeMillis()) }
                }
            }) { Text("模拟支付宝付款") }
        }
        if (items.isEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text(
                if (listenerConnected) "监听已连接但暂未收到新通知。" else
                    "监听未连接——已自动尝试重连；仍失败请点「重连」，或到系统设置把「通知使用权」关闭再开启，必要时重启手机。恢复后先打开一次 PayLedger 再付款。",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(items.size) { i ->
                val n = items[i]
                val noAmount = !n.text.contains('¥') && !n.text.contains('￥') && !n.text.contains("元")
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "${n.packageName}${if (n.source != "通知") "  [${n.source}]" else ""}  ${timeFmt.format(Date(n.postedAt))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text("标题：${n.title}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "文本：${n.text}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (n.packageName == "com.tencent.mm" && noAmount) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "⚠ 该通知不含金额。若是付款通知：微信 → 我 → 设置 → 新消息通知 → 打开「通知显示消息详情」后重试。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}
