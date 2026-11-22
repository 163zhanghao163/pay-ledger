package com.zhaoyingqi.payledger.ui

import android.content.ComponentName
import android.os.Bundle
import android.service.notification.NotificationListenerService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
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
import com.zhaoyingqi.payledger.notification.NotificationLedgerService
import com.zhaoyingqi.payledger.ui.theme.PayLedgerTheme

class MainActivity : ComponentActivity() {

    private var listenerEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        listenerEnabled = isListenerEnabled()
        setContent {
            PayLedgerTheme {
                if (listenerEnabled) MainShell() else AuthorizationScreen()
            }
        }
        // 从系统设置返回后重新检测授权状态；有授权但监听未连接时（升级安装后系统不重绑）自动请求重连
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                listenerEnabled = isListenerEnabled()
                ensureListenerConnected()
            }
        })
    }

    @Composable
    private fun MainShell() {
        val app = LocalContext.current.applicationContext as PayLedgerApplication
        val vm: PayLedgerViewModel = viewModel(initializer = { PayLedgerViewModel(app.dao) })
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var showDebug by rememberSaveable { mutableStateOf(false) }
        var showHelp by rememberSaveable { mutableStateOf(false) }

        Scaffold(
            topBar = { TopBar(onDebug = { showDebug = true }, debugVisible = BuildConfig.DEBUG, onHelp = { showHelp = true }) },
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

    /** 有通知使用权但服务未连接（升级安装后系统未重绑、省电杀服务等）时主动请求重连，10 秒节流。 */
    private fun ensureListenerConnected() {
        if (!listenerEnabled || NotificationLedgerService.connected) return
        val now = System.currentTimeMillis()
        if (now - lastRebindAt < REBIND_THROTTLE_MS) return
        lastRebindAt = now
        try {
            NotificationListenerService.requestRebind(ComponentName(this, NotificationLedgerService::class.java))
        } catch (_: Exception) {
        }
    }

    private var lastRebindAt = 0L

    companion object { private const val REBIND_THROTTLE_MS = 10_000L }
}
