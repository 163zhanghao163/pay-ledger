package com.zhaoyingqi.payledger.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.zhaoyingqi.payledger.BuildConfig
import com.zhaoyingqi.payledger.PayLedgerApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** 被动监听付款通知：回调→解析→去重→入库。无轮询、无前台服务、无周期任务。 */
class NotificationLedgerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val app by lazy { application as PayLedgerApplication }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName
        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        var text = extractBody(extras)
        // 微信/支付宝的付款凭证常用自定义布局，正文可能只在 RemoteViews 里
        if (pkg == ParserRules.PACKAGE_WECHAT || pkg == ParserRules.PACKAGE_ALIPAY) {
            extractRemoteViewsText(sbn.notification)?.let { rv ->
                text = listOf(text, rv).filter { it.isNotBlank() }.joinToString("\n")
            }
        }
        // debug 构建记录所有通知（含其他 App），便于对照校准解析规则；release 零开销
        if (BuildConfig.DEBUG) app.recentNotifications.push(RawNotification(pkg, title, text, sbn.postTime))

        if (pkg != ParserRules.PACKAGE_WECHAT && pkg != ParserRules.PACKAGE_ALIPAY) return
        val parsed = NotificationParser.parse(pkg, title, text)
        if (parsed == null) {
            if (BuildConfig.DEBUG) Log.d(TAG, "ignored: pkg=$pkg title=$title")
            return
        }
        val occurredAt = sbn.postTime
        scope.launch { save(parsed, occurredAt) }
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

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PayLedgerService"
        private const val MAX_REMOTE_TEXT_LEN = 500
    }
}
