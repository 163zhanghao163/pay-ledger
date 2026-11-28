package com.zhaoyingqi.payledger.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhaoyingqi.payledger.BuildConfig
import com.zhaoyingqi.payledger.PayLedgerApplication
import com.zhaoyingqi.payledger.data.TransactionDao
import com.zhaoyingqi.payledger.debug.EnergyCounters
import com.zhaoyingqi.payledger.debug.EnergySnapshot
import com.zhaoyingqi.payledger.debug.PerfTestData
import com.zhaoyingqi.payledger.notification.ListenerHealth
import com.zhaoyingqi.payledger.notification.ListenerStatus
import com.zhaoyingqi.payledger.notification.NotificationParser
import com.zhaoyingqi.payledger.notification.RawNotification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 仅 debug 构建可达（顶栏「调试」）：查看微信/支付宝最近通知 + 模拟付款注入。 */
@Composable
fun DebugPreviewScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as PayLedgerApplication
    var refreshTick by remember { mutableIntStateOf(0) }
    val entries = remember(refreshTick) { app.recentNotifications.snapshot() }
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val scope = rememberCoroutineScope()
    val listenerConnected = remember(refreshTick) { ListenerStatus.snapshot.value.notificationConnected }
    val lastConnectedText = remember(refreshTick) {
        ListenerHealth.lastConnectedAt(context).takeIf { it > 0 }?.let { timeFmt.format(Date(it)) }
    }
    val accessibilityEnabled = remember(refreshTick) { checkAccessibility(context) }

    // 状态行每 3 秒自动刷新（重连后需要片刻才生效）
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(3_000)
            refreshTick += 1
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SecondaryHeader(
                title = "调试预览",
                subtitle = "校准通知解析与双通道状态",
                onBack = onBack,
                actions = {
                    TextButton(onClick = { refreshTick += 1 }) { Text("刷新") }
                    TextButton(
                        onClick = {
                            app.recentNotifications.clear()
                            refreshTick += 1
                        },
                    ) { Text("清空") }
                },
            )
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        "此页只展示微信/支付宝最近的通知，用于校准解析规则；其他应用的通知不会进入缓冲。" +
                            "无障碍通道在系统层只监听这两个应用。数据仅存内存（最多 30 条、保留 7 天），不占存储。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            ChannelStatusCard(
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
                onOpenSettings = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                },
            )
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("模拟付款注入", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "直接走「解析 → 去重 → 入库」完整链路，无需真实支付。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    // 模板带当前时刻的付款时间，验证「文案时间解析入账、不标估算」路径
                                    val stamp = java.time.LocalDateTime.now()
                                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                                    NotificationParser.parse(
                                        "com.tencent.mm",
                                        "微信",
                                        "微信支付凭证\n付款成功 ¥9.99\n付款给 测试商户\n$stamp",
                                    )?.let { app.savePayment(it, System.currentTimeMillis()) }
                                    refreshTick += 1
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                        ) { Text("模拟微信付款") }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    NotificationParser.parse(
                                        "com.eg.android.AlipayGphone",
                                        "支付宝通知",
                                        "付款成功 ¥8.88 向 测试商户",
                                    )?.let { app.savePayment(it, System.currentTimeMillis()) }
                                    refreshTick += 1
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                        ) { Text("模拟支付宝付款") }
                    }
                }
            }
        }
        if (BuildConfig.DEBUG) {
            item {
                PerfDataCard(onChanged = { refreshTick += 1 }, refreshTick = refreshTick, scope = scope, dao = app.dao)
            }
            item {
                EnergyCounterCard(snapshot = remember(refreshTick) { EnergyCounters.snapshot() })
            }
        }
        if (entries.isEmpty()) {
            item {
                Text(
                    if (listenerConnected) {
                        "监听已连接但暂未收到新通知。"
                    } else {
                        "监听未连接——已自动尝试重连；仍失败请点「重连」，或到系统设置把「通知使用权」" +
                            "关闭再开启，必要时重启手机。恢复后先打开一次 PayLedger 再付款。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
        }
        items(entries.size) { index ->
            NotificationCard(entries[index], timeFmt)
        }
    }
}

/** v09 3.1：性能基线测试数据入口（仅 debug 构建显示，release 无此入口）。 */
@Composable
private fun PerfDataCard(
    onChanged: () -> Unit,
    refreshTick: Int,
    scope: CoroutineScope,
    dao: TransactionDao,
) {
    val testCount by produceState(0, refreshTick) {
        value = dao.countByCounterparty(PerfTestData.MARKER)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("性能测试数据", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(3.dp))
            Text(
                "生成 1 万条覆盖近 12 个月的确定性测试记录，用于按 PERF_BASELINE.md 的方法量耗时；" +
                    "清理只删测试标记行，不影响真实记账。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            dao.insertAll(PerfTestData.generate())
                            onChanged()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("生成 1 万条") }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            dao.deleteByCounterparty(PerfTestData.MARKER)
                            onChanged()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("清空测试数据") }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "当前测试数据：$testCount 条",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** bg D7：后台能耗计数器（仅 debug 构建显示），按 ENERGY_BASELINE.md 的方法观察渲染/遍历次数。 */
@Composable
private fun EnergyCounterCard(snapshot: EnergySnapshot) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("后台能耗计数", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(3.dp))
            Text(
                "进程启动以来事件驱动通道的工作量，用于按 ENERGY_BASELINE.md 对比改动前后；" +
                    "「影子差异」非 0 表示有内容被新门槛拦下但旧流程本可入账。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "收到事件 ${snapshot.receivedEvents} · 布局渲染 ${snapshot.renders} · " +
                    "窗口遍历 ${snapshot.traversals} · 读取节点 ${snapshot.nodesRead} · 影子差异 ${snapshot.shadowDiffs}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ChannelStatusCard(
    listenerConnected: Boolean,
    lastConnectedText: String?,
    accessibilityEnabled: Boolean,
    onRebind: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(active = listenerConnected)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text("通知监听", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (listenerConnected) "已连接 ✓" else "未连接 ✗",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (listenerConnected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(active = accessibilityEnabled, optional = true)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text("无障碍兜底", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (accessibilityEnabled) "已开启 ✓" else "未开启（可选）",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (accessibilityEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            if (!listenerConnected && lastConnectedText != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "上次连接：$lastConnectedText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!listenerConnected) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onRebind,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("重连")
                    }
                    OutlinedButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("设置")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusDot(active: Boolean, optional: Boolean = false) {
    val color = when {
        active -> MaterialTheme.colorScheme.primary
        optional -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.error
    }
    Box(Modifier.size(9.dp).clip(CircleShape).background(color))
}

@Composable
private fun NotificationCard(notification: RawNotification, timeFmt: SimpleDateFormat) {
    val noAmount = !notification.text.contains('¥') &&
        !notification.text.contains('￥') &&
        !notification.text.contains("元")
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChannelBadge(channelOf(notification.packageName), 34.dp)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        appLabel(notification.packageName),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "${notification.packageName} · ${timeFmt.format(Date(notification.postedAt))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (notification.source != "通知") {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            notification.source,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "标题：${notification.title.ifBlank { "（无标题）" }}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "文本：${notification.text}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
            )
            if (notification.packageName == "com.tencent.mm" && noAmount) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        "⚠ 该通知不含金额。若是付款通知：微信 → 我 → 设置 → 新消息通知 → " +
                            "打开「通知显示消息详情」后重试。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(9.dp),
                    )
                }
            }
        }
    }
}

private fun channelOf(packageName: String): String = when (packageName) {
    "com.tencent.mm" -> "WECHAT"
    "com.eg.android.AlipayGphone" -> "ALIPAY"
    else -> "OTHER"
}

private fun appLabel(packageName: String): String = when (packageName) {
    "com.tencent.mm" -> "微信"
    "com.eg.android.AlipayGphone" -> "支付宝"
    else -> "其他应用"
}
