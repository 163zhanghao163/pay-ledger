package com.zhaoyingqi.payledger.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 开机 / 应用升级 / 解锁后主动请求重新绑定通知监听，覆盖系统不自动重绑的场景。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // 只响应声明的系统广播，避免伪造 intent 触发
        if (intent?.action !in EXPECTED_ACTIONS) return
        if (!ListenerHealth.isAuthorized(context)) return
        ListenerHealth.requestRebind(context, force = true)
    }

    private companion object {
        val EXPECTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_USER_UNLOCKED,
        )
    }
}
