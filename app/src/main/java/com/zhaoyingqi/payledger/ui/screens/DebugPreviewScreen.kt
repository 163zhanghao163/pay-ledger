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
import com.zhaoyingqi.payledger.notification.NotificationParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/** 仅 debug 构建可达（顶栏「调试」进入）：查看最近收到的全部通知 + 模拟付款注入。 */
@Composable
fun DebugPreviewScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as PayLedgerApplication
    var refreshTick by remember { mutableIntStateOf(0) }
    val items = remember(refreshTick) { app.recentNotifications.snapshot() }
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("返回") }
            Text("最近通知（调试）", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { refreshTick += 1 }) { Text("刷新") }
        }
        Text(
            "记录所有 App 的通知。付款一笔后回到这里，找到对应通知发我校准规则。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
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
                "暂未收到通知——通知监听可能未生效（升级安装后请在系统设置把「通知使用权」关掉再开一次），或还没有新通知。",
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
                            "${n.packageName}  ${timeFmt.format(Date(n.postedAt))}",
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
