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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhaoyingqi.payledger.BuildConfig
import com.zhaoyingqi.payledger.PayLedgerApplication
import com.zhaoyingqi.payledger.R
import com.zhaoyingqi.payledger.notification.ListenerHealth
import com.zhaoyingqi.payledger.notification.NotificationLedgerService
import com.zhaoyingqi.payledger.ui.screens.AuthorizationScreen
import com.zhaoyingqi.payledger.ui.screens.BrandMark
import com.zhaoyingqi.payledger.ui.screens.DebugPreviewScreen
import com.zhaoyingqi.payledger.ui.screens.HelpScreen
import com.zhaoyingqi.payledger.ui.screens.RecordsScreen
import com.zhaoyingqi.payledger.ui.screens.StatsScreen
import com.zhaoyingqi.payledger.ui.theme.PayLedgerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class MainActivity : ComponentActivity() {

    private var listenerEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as PayLedgerApplication
        listenerEnabled = isListenerEnabled()
        setContent {
            PayLedgerTheme {
                if (listenerEnabled) MainShell() else AuthorizationScreen()
            }
        }
        // 从系统设置返回后重新检测授权状态；有授权但监听未连接时自动尝试重连
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                listenerEnabled = isListenerEnabled()
                ListenerHealth.ensureConnected(this@MainActivity, app.applicationScope)
            }
        })
    }

    @Composable
    private fun MainShell() {
        val context = LocalContext.current
        val app = context.applicationContext as PayLedgerApplication
        val vm: PayLedgerViewModel = viewModel(initializer = { PayLedgerViewModel(app.dao) })
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var showDebug by rememberSaveable { mutableStateOf(false) }
        var showHelp by rememberSaveable { mutableStateOf(false) }
        var listenerConnected by remember { mutableStateOf(NotificationLedgerService.connected) }
        val secondary = showDebug || showHelp

        // 连接状态由系统回调更新，界面每 2 秒轮询一次（开销可忽略）
        LaunchedEffect(Unit) {
            while (isActive) {
                delay(2_000)
                listenerConnected = NotificationLedgerService.connected
            }
        }

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
                        if (!listenerConnected) {
                            ListenerBanner(
                                onReconnect = { ListenerHealth.reconnect(context, app.applicationScope) },
                                onSettings = {
                                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                },
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
                    showHelp -> HelpScreen(onBack = { showHelp = false })
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
