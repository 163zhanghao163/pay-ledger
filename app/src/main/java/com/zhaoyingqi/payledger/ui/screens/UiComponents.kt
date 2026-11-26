package com.zhaoyingqi.payledger.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 品牌标识：与启动图标一致的青绿底 + ¥，用于顶栏和授权页。 */
@Composable
internal fun BrandMark(size: Dp = 44.dp) {
    Surface(
        modifier = Modifier.size(size),
        shape = RoundedCornerShape(size * 0.3f),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                "¥",
                style = if (size >= 64.dp) {
                    MaterialTheme.typography.displaySmall
                } else {
                    MaterialTheme.typography.titleLarge
                },
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** 渠道色块：微信绿、支付宝蓝、手动记账暖棕。 */
@Composable
internal fun ChannelBadge(channel: String, size: Dp = 40.dp) {
    val accent = channelAccent(channel)
    Surface(
        modifier = Modifier.size(size),
        shape = RoundedCornerShape(size * 0.32f),
        color = accent.copy(alpha = 0.14f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                channelMark(channel),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        }
    }
}

internal fun channelMark(channel: String): String = when (channel) {
    "WECHAT" -> "微"
    "ALIPAY" -> "支"
    else -> "记"
}

@Composable
internal fun channelAccent(channel: String): Color {
    val dark = isSystemInDarkTheme()
    return when (channel) {
        "WECHAT" -> if (dark) Color(0xFF5BE08E) else Color(0xFF078A4B)
        "ALIPAY" -> if (dark) Color(0xFF82B9FF) else Color(0xFF1268D6)
        else -> MaterialTheme.colorScheme.tertiary
    }
}

/** 带编号的步骤卡：授权页、帮助页共用。 */
@Composable
internal fun GuideStep(
    number: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    footer: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (highlight) {
            scheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            scheme.surfaceContainerLow
        },
        border = BorderStroke(
            1.dp,
            if (highlight) scheme.primary.copy(alpha = 0.28f) else scheme.outlineVariant.copy(alpha = 0.45f),
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Surface(
                modifier = Modifier.size(30.dp),
                shape = RoundedCornerShape(10.dp),
                color = if (highlight) scheme.primary else scheme.primaryContainer,
                contentColor = if (highlight) scheme.onPrimary else scheme.onPrimaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("$number", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
                Spacer(Modifier.height(5.dp))
                Text(body, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                if (footer != null) {
                    Spacer(Modifier.height(10.dp))
                    footer()
                }
            }
        }
    }
}

/** 二级页面顶栏（帮助、调试）：返回 + 标题 + 右侧操作。 */
@Composable
internal fun SecondaryHeader(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
        Column(Modifier.weight(1f).padding(start = 2.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        actions()
    }
}
