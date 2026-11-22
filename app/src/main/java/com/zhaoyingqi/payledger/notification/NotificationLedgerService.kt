package com.zhaoyingqi.payledger.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.zhaoyingqi.payledger.BuildConfig
import com.zhaoyingqi.payledger.PayLedgerApplication
import com.zhaoyingqi.payledger.data.TransactionRecord
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
        if (pkg != ParserRules.PACKAGE_WECHAT && pkg != ParserRules.PACKAGE_ALIPAY) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        app.recentNotifications.push(RawNotification(pkg, title.orEmpty(), text.orEmpty(), sbn.postTime))

        val parsed = NotificationParser.parse(pkg, title, text)
        if (parsed == null) {
            if (BuildConfig.DEBUG) Log.d(TAG, "ignored: pkg=$pkg title=$title")
            return
        }
        val occurredAt = sbn.postTime
        scope.launch { save(parsed, occurredAt) }
    }

    private suspend fun save(parsed: ParsedPayment, occurredAt: Long) {
        try {
            val window = DedupWindow.of(occurredAt)
            if (app.dao.findDuplicate(parsed.channel.name, parsed.amountCents, window.first, window.last) != null) return
            app.dao.insert(
                TransactionRecord(
                    amountCents = parsed.amountCents,
                    channel = parsed.channel.name,
                    counterparty = parsed.counterparty,
                    occurredAt = occurredAt,
                    summary = parsed.summary,
                    createdAt = System.currentTimeMillis(),
                ),
            )
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
