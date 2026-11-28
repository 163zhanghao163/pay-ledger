package com.zhaoyingqi.payledger.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhaoyingqi.payledger.BuildConfig
import com.zhaoyingqi.payledger.PayLedgerApplication
import com.zhaoyingqi.payledger.R
import com.zhaoyingqi.payledger.notification.ListenerHealth
import com.zhaoyingqi.payledger.notification.ListenerStatus
import com.zhaoyingqi.payledger.ui.screens.AuthorizationScreen
import com.zhaoyingqi.payledger.ui.screens.BrandMark
import com.zhaoyingqi.payledger.ui.screens.CategoryRulesScreen
import com.zhaoyingqi.payledger.ui.screens.DebugPreviewScreen
import com.zhaoyingqi.payledger.ui.screens.HelpScreen
import com.zhaoyingqi.payledger.ui.screens.RecordsScreen
import com.zhaoyingqi.payledger.ui.screens.StatsScreen
import com.zhaoyingqi.payledger.ui.screens.checkAccessibility
import com.zhaoyingqi.payledger.ui.theme.PayLedgerTheme

class MainActivity : ComponentActivity() {

    private var listenerEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as PayLedgerApplication
        listenerEnabled = isListenerEnabled()
        syncListenerStatus()
        setContent {
            PayLedgerTheme {
                if (listenerEnabled) MainShell() else AuthorizationScreen()
            }
        }
        // 从系统设置返回后重新检测授权状态；有授权但监听未连接时自动尝试重连
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                listenerEnabled = isListenerEnabled()
                syncListenerStatus()
                ListenerHealth.ensureConnected(this@MainActivity, app.applicationScope)
            }
        })
    }

    /** 无障碍状态值变化时应用必然经过一次 ON_RESUME，这里读一次即可（v08 D2）。 */
    private fun syncListenerStatus() {
        ListenerStatus.setAuthorized(listenerEnabled)
        ListenerStatus.setAccessibilityConnected(checkAccessibility(this))
    }

    @Composable
    private fun MainShell() {
        val context = LocalContext.current
        val app = context.applicationContext as PayLedgerApplication
        val vm: PayLedgerViewModel = viewModel(
            initializer = {
                PayLedgerViewModel(app.dao, appContext = app, ruleDao = app.database.categoryRuleDao())
            },
        )
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var showDebug by rememberSaveable { mutableStateOf(false) }
        var showHelp by rememberSaveable { mutableStateOf(false) }
        var showRules by rememberSaveable { mutableStateOf(false) }
        val status by ListenerStatus.snapshot.collectAsStateWithLifecycle()
        val secondary = showDebug || showHelp || showRules

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                if (!secondary) {
                    Column {
                        AppHeader(
                            onDebug = { showDebug = true },
                            debugVisible = BuildConfig.DEBUG,
                            onHelp = { showHelp = true },
                        )
                        when {
                            // 断开横幅：有授权但监听未在工作（v08 D2）
                            !status.notificationConnected -> ListenerBanner(
                                onReconnect = { ListenerHealth.reconnect(context, app.applicationScope) },
                                onSettings = {
                                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                },
                            )
                            // 低强调提示行：监听正常但无障碍未开启，前台支付可能漏记（v08 1.3/D2）
                            !status.accessibilityConnected -> AccessibilityHintRow(
                                onOpen = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                    }
                }
            },
            bottomBar = {
                if (!secondary) {
                    AppBottomBar(
                        selectedTab = tab,
                        onSelect = { tab = it },
                    )
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when {
                    showDebug -> DebugPreviewScreen(onBack = { showDebug = false })
                    showRules -> CategoryRulesScreen(vm, onBack = { showRules = false })
                    showHelp -> HelpScreen(onBack = { showHelp = false }, onOpenRules = { showRules = true })
                    tab == 0 -> RecordsScreen(vm)
                    else -> StatsScreen(vm, onJumpToList = { tab = 0 })
                }
            }
        }
    }

    private fun isListenerEnabled(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
}

@Composable
private fun AppHeader(onDebug: () -> Unit, debugVisible: Boolean, onHelp: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandMark(42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "PayLedger",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "微信 · 支付宝自动记账",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onHelp) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = "设置指南",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (debugVisible) {
                IconButton(onClick = onDebug) {
                    Icon(
                        Icons.Filled.Build,
                        contentDescription = "调试",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppBottomBar(selectedTab: Int, onSelect: (Int) -> Unit) {
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            NavigationBarItem(
                selected = selectedTab == 0,
                onClick = { onSelect(0) },
                icon = { Icon(painterResource(R.drawable.ic_nav_records), contentDescription = null) },
                label = { Text("明细") },
                colors = itemColors,
            )
            NavigationBarItem(
                selected = selectedTab == 1,
                onClick = { onSelect(1) },
                icon = { Icon(painterResource(R.drawable.ic_nav_stats), contentDescription = null) },
                label = { Text("统计") },
                colors = itemColors,
            )
        }
    }
}

/** 有权限但监听未连接时的顶部提示；点击重连会尝试重置监听组件。 */
@Composable
private fun ListenerBanner(onReconnect: () -> Unit, onSettings: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        color = scheme.errorContainer,
        contentColor = scheme.onErrorContainer,
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("通知监听未连接", style = MaterialTheme.typography.titleSmall)
                Text(
                    "可能漏记账，请尝试重连",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onErrorContainer.copy(alpha = 0.78f),
                )
            }
            TextButton(
                onClick = onReconnect,
                colors = ButtonDefaults.textButtonColors(contentColor = scheme.onErrorContainer),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("重连")
            }
            TextButton(
                onClick = onSettings,
                colors = ButtonDefaults.textButtonColors(contentColor = scheme.onErrorContainer),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("设置")
            }
        }
    }
}

/** 低强调提示行（v08 D2）：监听正常但无障碍未开启，前台支付可能漏记。 */
@Composable
private fun AccessibilityHintRow(onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        color = scheme.surfaceContainerHigh,
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Build,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "未开启无障碍，前台支付可能漏记",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onOpen,
                colors = ButtonDefaults.textButtonColors(contentColor = scheme.primary),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) { Text("去开启") }
        }
    }
}

// —— 横幅预览（v08 5.2，亮/暗）——

@Preview(name = "断连横幅（亮）", showBackground = true)
@Composable
private fun ListenerBannerLightPreview() {
    MaterialTheme { ListenerBanner(onReconnect = {}, onSettings = {}) }
}

@Preview(name = "断连横幅（暗）", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ListenerBannerDarkPreview() {
    MaterialTheme { ListenerBanner(onReconnect = {}, onSettings = {}) }
}

@Preview(name = "无障碍提示行（亮）", showBackground = true)
@Composable
private fun AccessibilityHintLightPreview() {
    MaterialTheme { AccessibilityHintRow(onOpen = {}) }
}

@Preview(name = "无障碍提示行（暗）", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AccessibilityHintDarkPreview() {
    MaterialTheme { AccessibilityHintRow(onOpen = {}) }
}
