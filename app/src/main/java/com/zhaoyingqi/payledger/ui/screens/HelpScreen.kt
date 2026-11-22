package com.zhaoyingqi.payledger.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** 设置指南：任何状态都可达（顶栏「帮助」），不受授权状态影响。 */
@Composable
fun HelpScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("返回") }
                Text("设置指南", style = MaterialTheme.typography.titleMedium)
            }
        }
        item {
            Step(
                title = "第 1 步：开启系统「通知使用权」",
                body = "点下方按钮，在列表中找到 PayLedger 并打开开关。\n刚升级或重装过应用的话，请先关闭再重新打开一次。",
            )
        }
        item {
            Button(onClick = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }) { Text("去开启通知使用权") }
        }
        item { HorizontalDivider() }
        item {
            Step(
                title = "第 2 步（关键）：微信打开「通知显示消息详情」",
                body = "微信 → 我 → 设置 → 新消息通知 → 打开「通知显示消息详情」。\n" +
                    "此开关关闭时，付款通知里没有金额，任何记账应用都无法自动记账。",
            )
        }
        item {
            Step(
                title = "第 3 步：允许微信显示系统通知",
                body = "系统设置 → 应用 → 微信 → 通知管理：打开「允许通知」，并勾选横幅 / 锁屏显示。",
            )
        }
        item {
            Step(
                title = "第 4 步：允许 PayLedger 后台运行",
                body = "系统设置中允许 PayLedger「自启动」并关闭电池优化（部分国产 ROM 需要）。",
            )
        }
        item {
            val context = LocalContext.current
            var accEnabled by remember { mutableStateOf(checkAccessibility(context)) }
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) accEnabled = checkAccessibility(context)
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            Column {
                Step(
                    title = "第 5 步（推荐）：开启「无障碍」兜底",
                    body = "付款时若微信/支付宝正好在前台，系统可能不发通知栏通知，通知监听就会漏记。" +
                        "开启 PayLedger 的无障碍服务后，可从支付成功页面读取金额补记。" +
                        "只监听微信/支付宝两个应用、耗电极小、不上传数据。",
                )
                Text(
                    if (accEnabled) "当前状态：已开启 ✓" else "当前状态：未开启",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (accEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Button(
                    onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("去开启无障碍") }
            }
        }
        item { HorizontalDivider() }
        item {
            Step(
                title = "验证",
                body = "付款一笔小额：通知栏能看到金额字样即设置成功，打开 PayLedger 会自动出现记录。\n" +
                    "也可先在调试页点「模拟微信付款」验证记账链路（仅调试版）。",
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun Step(title: String, body: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 检测本应用的无障碍服务是否已开启（帮助页经 ON_RESUME 刷新，调试页随「刷新」重读）。 */
internal fun checkAccessibility(context: android.content.Context): Boolean {
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
    return enabled?.contains(context.packageName) == true
}
