package com.zhaoyingqi.payledger.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 开机 / 应用升级 / 解锁后主动请求重新绑定通知监听，覆盖系统不自动重绑的场景。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!ListenerHealth.isAuthorized(context)) return
        ListenerHealth.requestRebind(context, force = true)
    }
}
