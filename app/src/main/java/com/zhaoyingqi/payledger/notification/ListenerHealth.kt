package com.zhaoyingqi.payledger.notification

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 通知监听连接健康检查与自动重连。
 *
 * 国产 ROM 在进程被回收后不一定会重新 bind NotificationListenerService，仅调用
 * requestRebind 也可能被忽略。这里按「请求重绑 → 短暂等待 → 重置监听组件后再请求」逐级尝试，
 * 尽量复现用户在系统设置里关闭/开启通知使用权的效果；仍失败时由 UI 引导手动处理。
 */
object ListenerHealth {
    const val PREFS_NAME = "payledger_listener"

    private const val TAG = "PayLedgerListener"
    private const val KEY_LAST_CONNECTED = "last_connected_at"
    private const val KEY_LAST_REBIND = "last_rebind_at"
    private const val KEY_LAST_TOGGLE = "last_toggle_at"
    private const val REBIND_THROTTLE_MS = 10_000L
    private const val TOGGLE_THROTTLE_MS = 5 * 60_000L

    @Volatile
    private var reconnecting = false

    fun isAuthorized(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun lastConnectedAt(context: Context): Long = prefs(context).getLong(KEY_LAST_CONNECTED, 0L)

    fun markConnected(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_CONNECTED, System.currentTimeMillis()).apply()
    }

    /** 请求系统重新绑定监听服务；[force] = true 时忽略 10 秒节流。 */
    fun requestRebind(context: Context, force: Boolean = false): Boolean {
        if (!isAuthorized(context)) return false
        val app = context.applicationContext
        val p = prefs(app)
        val now = System.currentTimeMillis()
        if (!force && now - p.getLong(KEY_LAST_REBIND, 0L) < REBIND_THROTTLE_MS) return false
        p.edit().putLong(KEY_LAST_REBIND, now).apply()
        return try {
            NotificationListenerService.requestRebind(component(app))
            true
        } catch (e: Exception) {
            Log.w(TAG, "requestRebind failed", e)
            false
        }
    }

    /** 应用回到前台 / 无障碍服务连上时调用；未连接才尝试，避免打扰系统。 */
    fun ensureConnected(context: Context, scope: CoroutineScope) {
        if (!isAuthorized(context) || NotificationLedgerService.connected) return
        reconnect(context, scope)
    }

    /** 逐级重连：requestRebind → 等待 → （节流内未尝试过则）重置组件并再次重绑。 */
    fun reconnect(context: Context, scope: CoroutineScope) {
        if (!isAuthorized(context) || NotificationLedgerService.connected) return
        synchronized(this) {
            if (reconnecting) return
            reconnecting = true
        }
        val app = context.applicationContext
        scope.launch {
            try {
                if (requestRebind(app, force = true)) delay(1_500)
                if (NotificationLedgerService.connected) return@launch
                if (shouldToggle(app)) {
                    toggleComponent(app)
                    requestRebind(app, force = true)
                    delay(2_000)
                }
            } finally {
                reconnecting = false
            }
        }
    }

    private fun shouldToggle(context: Context): Boolean =
        System.currentTimeMillis() - prefs(context).getLong(KEY_LAST_TOGGLE, 0L) >= TOGGLE_THROTTLE_MS

    /** 重置监听组件，尽量触发系统重新绑定；失败不影响应用其他功能。 */
    private fun toggleComponent(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_TOGGLE, System.currentTimeMillis()).apply()
        val pm = context.packageManager
        val comp = component(context)
        try {
            pm.setComponentEnabledSetting(
                comp,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
            pm.setComponentEnabledSetting(
                comp,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            Log.i(TAG, "listener component reset")
        } catch (e: Exception) {
            Log.w(TAG, "reset listener component failed", e)
        }
    }

    private fun component(context: Context) =
        ComponentName(context, NotificationLedgerService::class.java)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
