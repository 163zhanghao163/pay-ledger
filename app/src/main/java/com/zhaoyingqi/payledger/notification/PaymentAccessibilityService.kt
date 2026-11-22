package com.zhaoyingqi.payledger.notification

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.zhaoyingqi.payledger.PayLedgerApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 无障碍兜底：付款时微信/支付宝常在前台，系统可能不发通知栏通知，通知监听会漏。
 * 只监听微信/支付宝两个包名（见 xml/accessibility_service_config.xml），读取支付成功页面文本补记；
 * 解析与入库复用 NotificationParser + savePayment（含 ±3 秒去重，与通知通道天然互斥）。
 */
class PaymentAccessibilityService : AccessibilityService() {

    private val app by lazy { application as PayLedgerApplication }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastHandledAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg != ParserRules.PACKAGE_WECHAT && pkg != ParserRules.PACKAGE_ALIPAY) return

        val now = System.currentTimeMillis()
        // CONTENT_CHANGED 是高频事件（列表滚动、输入等），800ms 节流；页面切换（STATE_CHANGED）不节流
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && now - lastHandledAt < THROTTLE_MS) return
        lastHandledAt = now

        val texts = LinkedHashSet<String>()
        event.text?.forEach { t -> t?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { texts.add(it) } }

        var parsed = NotificationParser.parse(pkg, null, texts.joinToString("\n"))
        if (parsed == null) {
            // 事件自带文本没有金额时，读取窗口内容兜底（支付成功页的金额常在子节点里）
            collectWindowTexts(texts)
            parsed = NotificationParser.parse(pkg, null, texts.joinToString("\n"))
        }
        val combined = texts.joinToString("\n")
        if (combined.isEmpty()) return
        // 调试缓冲只保留与付款相关的内容，避免聊天等页面把付款通知挤出缓冲
        if (parsed != null || isPaymentRelated(combined)) {
            app.recentNotifications.push(RawNotification(pkg, "", combined, now, "无障碍"))
        }
        parsed ?: return
        scope.launch {
            try {
                app.savePayment(parsed, now)
            } catch (e: Exception) {
                Log.e(TAG, "save failed", e) // 不写捕获文本，避免敏感信息落日志
            }
        }
    }

    private fun collectWindowTexts(out: LinkedHashSet<String>) {
        val root = rootInActiveWindow ?: return
        try {
            walk(root, out, 0)
        } catch (_: Exception) {
            // 窗口读取失败（界面切换瞬间等）忽略本次
        }
    }

    private fun walk(node: AccessibilityNodeInfo, out: LinkedHashSet<String>, depth: Int) {
        if (depth > MAX_DEPTH || out.size > MAX_NODES) return
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { walk(it, out, depth + 1) }
        }
    }

    private fun isPaymentRelated(text: String): Boolean =
        text.contains("支付") || text.contains("付款") || text.contains("元") || text.contains('¥') || text.contains('￥')

    override fun onInterrupt() {}

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PayLedgerA11y"
        private const val THROTTLE_MS = 800L
        private const val MAX_DEPTH = 6
        private const val MAX_NODES = 120
    }
}
