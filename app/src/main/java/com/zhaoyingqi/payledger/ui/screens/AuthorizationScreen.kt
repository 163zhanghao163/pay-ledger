package com.zhaoyingqi.payledger.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun AuthorizationScreen() {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("PayLedger", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            "通过读取付款通知自动记账，数据只保存在本机，本应用不申请网络权限。\n\n" +
                "第 1 步：点下方按钮，开启系统「通知使用权」。\n\n" +
                "第 2 步（关键）：在微信内开启消息详情，否则付款通知里没有金额、无法记账：\n" +
                "微信 → 我 → 设置 → 新消息通知 → 打开「通知显示消息详情」。",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }) {
            Text("去开启通知使用权")
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "记账在后台自动完成，无需保持应用打开。\n" +
                "若长期未自动记录：请在系统设置中允许本应用「自启动」并关闭电池优化（部分国产 ROM 需要）；\n" +
                "重新安装或升级本应用后，请到系统设置把「通知使用权」关闭再重新开启一次。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
