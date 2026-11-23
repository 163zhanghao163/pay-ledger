package com.zhaoyingqi.payledger.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.zhaoyingqi.payledger.BuildConfig
import com.zhaoyingqi.payledger.PayLedgerApplication
import kotlinx.coroutines.launch

/** 被动监听付款通知：回调→解析→去重→入库。无轮询、无前台服务、无周期任务。 */
class NotificationLedgerService : NotificationListenerService() {

    private val app by lazy { application as PayLedgerApplication }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName
        // 只处理微信/支付宝；其他应用的通知直接丢弃，不解析、不进调试缓冲。
        if (!isTargetPackage(pkg)) return
        connected = true
        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        var text = extractBody(extras)
        // 微信/支付宝的付款凭证常用自定义布局，正文可能只在 RemoteViews 里
        extractRemoteViewsText(sbn.notification)?.let { rv ->
            text = listOf(text, rv).filter { it.isNotBlank() }.joinToString("\n")
        }
        // debug 构建只记录微信/支付宝通知，便于校准解析规则；release 零开销
        if (BuildConfig.DEBUG) app.recentNotifications.push(RawNotification(pkg, title, text, sbn.postTime))

        val parsed = NotificationParser.parse(pkg, title, text)
        if (parsed == null) {
            if (BuildConfig.DEBUG) Log.d(TAG, "ignored: pkg=$pkg title=$title")
            return
        }
        val occurredAt = sbn.postTime
        app.applicationScope.launch { save(parsed, occurredAt) }
    }

    /** 正文提取：TEXT → BIG_TEXT → TEXT_LINES（MessagingStyle，如微信消息式通知）。 */
    private fun extractBody(extras: android.os.Bundle): String = buildList {
        extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }?.let { add(it) }
        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.takeIf { it.isNotBlank() }?.let { add(it) }
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.filter { !it.isNullOrBlank() }?.map { it.toString() }?.takeIf { it.isNotEmpty() }
            ?.let { add(it.joinToString("\n")) }
    }.joinToString("\n")

    /** 自定义布局通知：把 RemoteViews 渲染成视图树后收集 TextView 文本（不显示 UI，瞬时完成）。 */
    private fun extractRemoteViewsText(notification: Notification): String? = try {
        val views = notification.bigContentView ?: notification.contentView ?: return null
        val root = views.apply(applicationContext, null)
        val out = StringBuilder()
        walkViews(root, out)
        out.toString().trim().takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }

    private fun walkViews(view: android.view.View, out: StringBuilder) {
        if (out.length > MAX_REMOTE_TEXT_LEN) return
        if (view is android.widget.TextView) {
            view.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.appendLine(it) }
        }
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) walkViews(view.getChildAt(i), out)
        }
    }

    private suspend fun save(parsed: ParsedPayment, occurredAt: Long) {
        try {
            app.savePayment(parsed, occurredAt)
        } catch (e: Exception) {
            Log.e(TAG, "save failed", e) // 不写通知文本，避免敏感信息落日志
        }
    }

    override fun onListenerConnected() {
        connected = true
        ListenerHealth.markConnected(app)
        super.onListenerConnected()
    }

    override fun onListenerDisconnected() {
        connected = false
        // 系统断开（升级安装/省电杀服务）后主动请求重连（内部有节流），避免静默失效
        ListenerHealth.requestRebind(app)
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        connected = false
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PayLedgerService"
        private const val MAX_REMOTE_TEXT_LEN = 500

        private fun isTargetPackage(pkg: String): Boolean =
            pkg == ParserRules.PACKAGE_WECHAT || pkg == ParserRules.PACKAGE_ALIPAY

        /** 监听是否已连接（onListenerConnected ↔ 断开），供 UI 检测"有权限但没在工作"的状态。 */
        @Volatile
        var connected = false
            private set
    }
}
