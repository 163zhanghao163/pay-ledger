package com.zhaoyingqi.payledger.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** 设置指南：任何状态都可达（顶栏「帮助」），不受授权状态影响。 */
@Composable
fun HelpScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var accEnabled by remember { mutableStateOf(checkAccessibility(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) accEnabled = checkAccessibility(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
    ) {
        item {
            SecondaryHeader(
                title = "设置指南",
                subtitle = "完成设置后，付款即自动记账",
                onBack = onBack,
            )
        }
        item {
            GuideStep(
                number = 1,
                title = "开启系统「通知使用权」",
                body = "在系统列表中找到 PayLedger 并打开开关。刚升级或重装过应用时，" +
                    "请先把开关关闭再重新打开一次；打开 App 时也会自动尝试重连。",
                footer = {
                    Button(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text("去开启通知使用权")
                    }
                },
            )
        }
        item {
            GuideStep(
                number = 2,
                title = "微信打开「通知显示消息详情」",
                body = "微信 → 我 → 设置 → 新消息通知 → 打开「通知显示消息详情」。\n" +
                    "此开关关闭时，付款通知里没有金额，任何记账应用都无法自动记账。",
                highlight = true,
            )
        }
        item {
            GuideStep(
                number = 3,
                title = "允许微信显示系统通知",
                body = "系统设置 → 应用 → 微信 → 通知管理：打开「允许通知」，并勾选横幅 / 锁屏显示。",
            )
        }
        item {
            GuideStep(
                number = 4,
                title = "允许 PayLedger 后台运行",
                body = "系统设置中允许 PayLedger「自启动」并关闭电池优化（部分国产 ROM 需要）。\n" +
                    "开机、应用升级后会自动请求重绑通知监听。",
            )
        }
        item {
            GuideStep(
                number = 5,
                title = "开启「无障碍」兜底（推荐）",
                body = "付款时若微信/支付宝正好在前台，系统可能不发通知栏通知，通知监听就会漏记。" +
                    "开启 PayLedger 的无障碍服务后，可从支付成功页面读取金额补记。" +
                    "它只监听微信/支付宝两个应用、耗电极小、不上传数据，也能帮通知监听在后台恢复连接。",
                footer = {
                    Column {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (accEnabled) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            },
                        ) {
                            Text(
                                if (accEnabled) "当前状态：已开启 ✓" else "当前状态：未开启",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (accEnabled) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer
                                },
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text(if (accEnabled) "管理无障碍" else "去开启无障碍")
                        }
                    }
                },
            )
        }
        item {
            GuideStep(
                number = 6,
                title = "验证自动记账",
                body = "付款一笔小额：通知栏能看到金额字样即设置成功，打开 PayLedger 会自动出现记录。\n" +
                    "也可先在调试页点「模拟微信付款」验证记账链路（仅调试版）。",
            )
        }
    }
}

/** 检测本应用的无障碍服务是否已开启（帮助页经 ON_RESUME 刷新，调试页随「刷新」重读）。 */
internal fun checkAccessibility(context: android.content.Context): Boolean {
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
    return enabled?.contains(context.packageName) == true
}
