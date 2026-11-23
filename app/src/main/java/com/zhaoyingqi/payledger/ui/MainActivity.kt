package com.zhaoyingqi.payledger.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhaoyingqi.payledger.BuildConfig
import com.zhaoyingqi.payledger.PayLedgerApplication
import com.zhaoyingqi.payledger.R
import com.zhaoyingqi.payledger.ui.screens.AuthorizationScreen
import com.zhaoyingqi.payledger.ui.screens.DebugPreviewScreen
import com.zhaoyingqi.payledger.ui.screens.HelpScreen
import com.zhaoyingqi.payledger.ui.screens.RecordsScreen
import com.zhaoyingqi.payledger.ui.screens.StatsScreen
import com.zhaoyingqi.payledger.notification.ListenerHealth
import com.zhaoyingqi.payledger.notification.NotificationLedgerService
import com.zhaoyingqi.payledger.ui.theme.PayLedgerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class MainActivity : ComponentActivity() {

    private var listenerEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
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

        // 连接状态由系统回调更新，界面每 2 秒轮询一次（开销可忽略）
        LaunchedEffect(Unit) {
            while (isActive) {
                delay(2_000)
                listenerConnected = NotificationLedgerService.connected
            }
        }

        Scaffold(
            topBar = {
                Column {
                    TopBar(
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
                }
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { Icon(painterResource(R.drawable.ic_nav_records), contentDescription = null) },
                        label = { Text("明细") },
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = { Icon(painterResource(R.drawable.ic_nav_stats), contentDescription = null) },
                        label = { Text("统计") },
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

    @Composable
    private fun TopBar(onDebug: () -> Unit, debugVisible: Boolean, onHelp: () -> Unit) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("PayLedger", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onHelp) { Text("帮助") }
            if (debugVisible) TextButton(onClick = onDebug) { Text("调试") }
        }
    }

    private fun isListenerEnabled(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
}

/** 有权限但监听未连接时的顶部提示；点击重连会尝试重置监听组件。 */
@Composable
private fun ListenerBanner(onReconnect: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "通知监听未连接，可能漏记账",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onReconnect) { Text("重连") }
        TextButton(onClick = onSettings) { Text("设置") }
    }
}
