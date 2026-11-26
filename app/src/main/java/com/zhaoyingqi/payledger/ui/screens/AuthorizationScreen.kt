package com.zhaoyingqi.payledger.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun AuthorizationScreen() {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme

    Column(
        Modifier.fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandMark(76.dp)
        Spacer(Modifier.height(18.dp))
        Text("PayLedger", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "微信 · 支付宝自动记账",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = scheme.primary,
            contentColor = scheme.onPrimary,
            shadowElevation = 4.dp,
        ) {
            Box(Modifier.clip(MaterialTheme.shapes.extraLarge)) {
                Box(
                    Modifier.align(Alignment.TopEnd)
                        .offset(x = 52.dp, y = (-52).dp)
                        .size(160.dp)
                        .clip(CircleShape)
                        .background(scheme.onPrimary.copy(alpha = 0.08f)),
                )
                Column(Modifier.padding(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(44.dp),
                            shape = CircleShape,
                            color = scheme.onPrimary.copy(alpha = 0.16f),
                            contentColor = scheme.onPrimary,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Notifications, contentDescription = null, modifier = Modifier.size(22.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "第 1 步 · 自动记账",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onPrimary.copy(alpha = 0.72f),
                            )
                            Text("开启通知使用权", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "授权后，微信/支付宝的付款通知会自动解析金额并记入本机账本，无需保持应用打开。",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onPrimary.copy(alpha = 0.84f),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = scheme.onPrimary,
                            contentColor = scheme.primary,
                        ),
                    ) {
                        Text("去开启通知使用权", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Column(Modifier.fillMaxWidth()) {
            Text("还需要两步", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(3.dp))
            Text(
                "设置完成后，付款通知会自动记账。",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        GuideStep(
            number = 2,
            title = "微信开启「通知显示消息详情」",
            body = "微信 → 我 → 设置 → 新消息通知 → 打开「通知显示消息详情」。\n" +
                "否则付款通知里没有金额，无法自动记账。",
            highlight = true,
        )
        Spacer(Modifier.height(12.dp))
        GuideStep(
            number = 3,
            title = "允许 PayLedger 后台运行",
            body = "系统设置中允许「自启动」并关闭电池优化（部分国产 ROM 需要）。\n" +
                "开机、升级或解锁后会自动尝试重连通知监听。",
        )
        Spacer(Modifier.height(20.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = scheme.secondaryContainer.copy(alpha = 0.55f),
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = scheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("数据只留在本机", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "不申请网络权限，不上传数据，不读取其他应用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "重新安装或升级后若显示「通知监听未连接」，可在应用内点重连；仍失败时把通知使用权关闭再开启一次。",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
    }
}
