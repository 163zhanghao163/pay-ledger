package com.zhaoyingqi.payledger.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.app.NotificationManagerCompat
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.zhaoyingqi.payledger.ui.screens.AuthorizationScreen
import com.zhaoyingqi.payledger.ui.screens.MainListScreen
import com.zhaoyingqi.payledger.ui.theme.PayLedgerTheme

class MainActivity : ComponentActivity() {

    private var listenerEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        listenerEnabled = isListenerEnabled()
        setContent {
            PayLedgerTheme {
                if (listenerEnabled) MainListScreen() else AuthorizationScreen()
            }
        }
        // 从系统设置返回后重新检测授权状态
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) listenerEnabled = isListenerEnabled()
        })
    }

    private fun isListenerEnabled(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
}
