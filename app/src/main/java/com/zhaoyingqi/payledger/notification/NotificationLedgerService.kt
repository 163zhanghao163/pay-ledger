package com.zhaoyingqi.payledger.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.zhaoyingqi.payledger.BuildConfig
import com.zhaoyingqi.payledger.PayLedgerApplication
import com.zhaoyingqi.payledger.debug.EnergyCounters
import kotlinx.coroutines.launch

/** 被动监听付款通知：回调→解析→去重→入库。无轮询、无前台服务、无周期任务。 */
class NotificationLedgerService : NotificationListenerService() {

    private val app by lazy { application as PayLedgerApplication }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName
        // 只处理微信/支付宝；其他应用的通知直接丢弃，不解析、不进调试缓冲。
        if (!isTargetPackage(pkg)) return
        ListenerStatus.setNotificationConnected(true)
        if (BuildConfig.DEBUG) EnergyCounters.onEventReceived()
        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val extrasText = extractBody(extras)
        val receivedAt = sbn.postTime
        val incomeEnabled = app.incomeEnabled.value

        // bg D1 两段式：第一段只用 extras 文本判定，避免对每条通知渲染整棵 RemoteViews 视图树
        val decision = stage1Decision("$title\n$extrasText", incomeEnabled)
        val text = when (decision) {
            Stage1Decision.DROP -> {
                // debug 缓冲与日志行为保持：忽略词命中的通知仍可见，但不再渲染
                if (BuildConfig.DEBUG) {
                    app.recentNotifications.push(RawNotification(pkg, title, extrasText, receivedAt))
                    Log.d(TAG, "ignored: pkg=$pkg title=$title")
                }
                null
            }
            // extras 为空：正文只在布局里（付款凭证常用自定义布局），渲染布局文本
            Stage1Decision.RENDER_ONLY -> renderRemoteViews(sbn.notification)
            // 含付款信号：渲染并把布局文本并入正文（旧行为等价）
            Stage1Decision.RENDER_MERGE -> {
                val rendered = renderRemoteViews(sbn.notification)
                listOf(extrasText, rendered.orEmpty()).filter { it.isNotBlank() }.joinToString("\n")
            }
            // 有文本但无任何付款信号：只用 extras 文本解析，不渲染
            Stage1Decision.EXTRAS_ONLY -> extrasText
        }
        // debug 构建只记录微信/支付宝通知，便于校准解析规则；release 零开销（DROP 分支已在上面入缓冲）
        if (BuildConfig.DEBUG && decision != Stage1Decision.DROP) {
            text?.let { app.recentNotifications.push(RawNotification(pkg, title, it, receivedAt)) }
        }

        var parsed = text?.let {
            NotificationParser.parse(pkg, title, it, receivedAt = receivedAt, incomeEnabled = incomeEnabled)
        }

        // bg D7 影子比对（仅 debug）：跳过渲染的分支按旧流程（渲染全量合并）重跑，检测门槛漏记
        if (BuildConfig.DEBUG && (decision == Stage1Decision.DROP || decision == Stage1Decision.EXTRAS_ONLY)) {
            val oldText = listOf(extrasText, renderRemoteViews(sbn.notification).orEmpty())
                .filter { it.isNotBlank() }.joinToString("\n")
            val oldParsed = NotificationParser.parse(pkg, title, oldText, receivedAt = receivedAt, incomeEnabled = incomeEnabled)
            if (ShadowCompare.diffCount(oldParsed, parsed) > 0) {
                EnergyCounters.onShadowDiff()
                app.recentNotifications.push(RawNotification(pkg, "", ShadowCompare.DESCRIPTION, receivedAt, "影子"))
            }
        }

        if (parsed == null) {
            if (BuildConfig.DEBUG && decision != Stage1Decision.DROP) Log.d(TAG, "ignored: pkg=$pkg title=$title")
            return
        }
        app.applicationScope.launch { save(parsed, receivedAt) }
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
    private fun renderRemoteViews(notification: Notification): String? = try {
        val views = notification.bigContentView ?: notification.contentView ?: return null
        if (BuildConfig.DEBUG) EnergyCounters.onRender()
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
        ListenerStatus.setNotificationConnected(true)
        ListenerHealth.markConnected(app)
        super.onListenerConnected()
    }

    override fun onListenerDisconnected() {
        ListenerStatus.setNotificationConnected(false)
        // 系统断开（升级安装/省电杀服务）后主动请求重连（内部有节流），避免静默失效
        ListenerHealth.requestRebind(app)
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        ListenerStatus.setNotificationConnected(false)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PayLedgerService"
        private const val MAX_REMOTE_TEXT_LEN = 500

        private fun isTargetPackage(pkg: String): Boolean =
            pkg == ParserRules.PACKAGE_WECHAT || pkg == ParserRules.PACKAGE_ALIPAY
    }
}

/** bg D1 第一段决策结果（只用 extras 文本判定，渲染与否据此分支）。 */
internal enum class Stage1Decision {
    /** 忽略词命中且无付款/收入语义：直接丢弃，不渲染。 */
    DROP,
    /** extras 文本为空：正文只在布局里，仅渲染布局。 */
    RENDER_ONLY,
    /** 含付款信号：渲染并把布局文本并入正文。 */
    RENDER_MERGE,
    /** 有文本但无付款信号：只用 extras 解析，不渲染。 */
    EXTRAS_ONLY,
}

/**
 * bg D1 第一段判定（纯函数，便于 JVM 单测覆盖四条分支）。
 * 忽略词命中需排除付款关键词与收入语义：开启「记收入」时收款到账类文案由解析器走收入分支，
 * 不能在第一段被「收款到账」这个硬忽略词误杀。
 */
internal fun stage1Decision(extrasCombined: String, incomeEnabled: Boolean): Stage1Decision {
    val ignoreHit = ParserRules.HARD_IGNORE.any { extrasCombined.contains(it) }
    val payHit = ParserRules.PAY_KEYWORDS.any { extrasCombined.contains(it) }
    val incomeHit = incomeEnabled && ParserRules.INCOME_KEYWORDS.any { extrasCombined.contains(it) }
    if (ignoreHit && !payHit && !incomeHit) return Stage1Decision.DROP
    if (extrasCombined.isBlank()) return Stage1Decision.RENDER_ONLY
    return if (ParserRules.hasPaySignal(extrasCombined)) Stage1Decision.RENDER_MERGE else Stage1Decision.EXTRAS_ONLY
}
