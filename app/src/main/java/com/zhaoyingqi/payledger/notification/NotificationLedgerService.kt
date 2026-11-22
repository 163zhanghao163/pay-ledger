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
        val text = extractBody(extras)
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

    companion object { private const val TAG = "PayLedgerService" }
}
