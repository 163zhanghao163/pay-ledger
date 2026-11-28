package com.zhaoyingqi.payledger.notification

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.zhaoyingqi.payledger.BuildConfig
import com.zhaoyingqi.payledger.PayLedgerApplication
import com.zhaoyingqi.payledger.data.TransactionRecord
import com.zhaoyingqi.payledger.debug.EnergyCounters
import kotlinx.coroutines.launch

/**
 * 无障碍兜底：付款时微信/支付宝常在前台，系统可能不发通知栏通知，通知监听会漏。
 * 只监听微信/支付宝两个包名（见 xml/accessibility_service_config.xml），读取支付成功页面文本补记；
 * 解析与入库复用 NotificationParser + savePayment（含 ±10 秒去重，与通知通道天然互斥）。
 *
 * bg 迭代：内容变化事件先过节流与同窗口去重（时间维），再过付款信号门槛（内容维），
 * 无信号不遍历窗口树；遍历优先走事件源子树，source 为空或读取失败回退根节点；
 * getChild 返回的节点用完即回收。
 */
class PaymentAccessibilityService : AccessibilityService() {

    private val app by lazy { application as PayLedgerApplication }
    private val gate = A11yGate()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg != ParserRules.PACKAGE_WECHAT && pkg != ParserRules.PACKAGE_ALIPAY) return
        if (BuildConfig.DEBUG) EnergyCounters.onEventReceived()

        val now = System.currentTimeMillis()
        val isContentEvent = event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        // bg D4/D5：时间窗判定最先执行——被节流/去重的事件不读任何文本，也不刷新时间戳
        if (gate.isContentThrottled(isContentEvent, now)) return
        if (gate.isWindowDeduped(event.windowId, isContentEvent, now)) return

        val texts = LinkedHashSet<String>()
        event.text?.forEach { t -> t?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { texts.add(it) } }

        var parsed = parseA11y(pkg, texts.joinToString("\n"), now)
        if (parsed == null) {
            // bg D2 门槛：event.text 未命中时才取 source 文本（一次 IPC），并入候选后判定
            val source = event.source
            source?.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { texts.add(it) }
            parsed = parseA11y(pkg, texts.joinToString("\n"), now)
            if (parsed == null && !ParserRules.hasPaySignal(texts.joinToString("\n"))) {
                // 无付款信号：不遍历窗口树、不刷新节流与窗口时间戳；debug 下按旧流程影子比对
                if (BuildConfig.DEBUG) shadowCompare(event, pkg, now)
                return
            }
            // 有信号但解析不出：读窗口树兜底（bg D3 源子树优先，回退见 collectWindowTexts）
            val nodeCount = collectWindowTexts(texts, source)
            if (BuildConfig.DEBUG && nodeCount > 0) EnergyCounters.onTraversal(nodeCount)
            parsed = parseA11y(pkg, texts.joinToString("\n"), now)
        }

        // 调试缓冲只保留与付款相关的内容：走到这里的事件必然已通过信号门槛或解析成功
        app.recentNotifications.push(RawNotification(pkg, "", texts.joinToString("\n"), now, "无障碍"))
        // bg D4/D5：节流与窗口时间戳只在通过信号门槛后刷新，且只记内容变化事件——
        // 页面切换若记时间戳，会把紧随其后携带金额的内容变化事件压掉
        if (isContentEvent) {
            gate.refreshThrottle(now)
            gate.markWindowProcessed(event.windowId, now)
        }

        parsed ?: return
        app.applicationScope.launch {
            try {
                app.savePayment(parsed, now, TransactionRecord.SOURCE_ACCESSIBILITY)
            } catch (e: Exception) {
                Log.e(TAG, "save failed", e) // 不写捕获文本，避免敏感信息落日志
            }
        }
    }

    private fun parseA11y(pkg: String, text: String, receivedAt: Long): ParsedPayment? =
        NotificationParser.parse(pkg, null, text, ParserRules.A11Y_PAY_KEYWORDS, receivedAt)

    /**
     * bg D3：优先遍历事件源子树；source 为空或读取异常才回退 rootInActiveWindow。
     * 返回读取的节点数（debug 能耗计数用）。
     */
    private fun collectWindowTexts(out: LinkedHashSet<String>, source: AccessibilityNodeInfo?): Int {
        if (source != null) {
            val count = tryWalk(source, out)
            if (count > 0) return count
        }
        return tryWalk(rootInActiveWindow, out)
    }

    /** 遍历起点包装：节点为空或读取异常（界面切换瞬间等）返回 0，不向上抛。 */
    private fun tryWalk(start: AccessibilityNodeInfo?, out: LinkedHashSet<String>): Int {
        if (start == null) return 0
        return try {
            walk(start, out, 0)
        } catch (_: Exception) {
            // 窗口读取失败（界面切换瞬间等）忽略本次
            0
        }
    }

    /** 深度优先收集节点文本，返回读取的节点数；深度与节点数上限见 [MAX_DEPTH]/[MAX_NODES]。 */
    @Suppress("DEPRECATION")
    private fun walk(node: AccessibilityNodeInfo, out: LinkedHashSet<String>, depth: Int): Int {
        if (depth > MAX_DEPTH || out.size > MAX_NODES) return 0
        var nodes = 1
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            nodes += walk(child, out, depth + 1)
            child.recycle() // bg D6：用完即释放；API 33+ 为空操作，旧版本避免依赖 GC 回收 native 对象
        }
        return nodes
    }

    /**
     * bg D7 影子比对（仅 debug 构建调用）：被门槛拦下的事件按旧流程重跑——只用 event.text，
     * 解析失败则根节点全量遍历后再解析；旧流程能入账而新流程没有则计数 +1。
     */
    private fun shadowCompare(event: AccessibilityEvent, pkg: String, now: Long) {
        val oldTexts = LinkedHashSet<String>()
        event.text?.forEach { t -> t?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { oldTexts.add(it) } }
        var oldParsed = parseA11y(pkg, oldTexts.joinToString("\n"), now)
        if (oldParsed == null) {
            collectWindowTexts(oldTexts, null)
            oldParsed = parseA11y(pkg, oldTexts.joinToString("\n"), now)
        }
        if (ShadowCompare.diffCount(oldParsed, null) > 0) {
            EnergyCounters.onShadowDiff()
            app.recentNotifications.push(RawNotification(pkg, "", ShadowCompare.DESCRIPTION, now, "影子"))
        }
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        ListenerStatus.setAccessibilityConnected(true)
        // 无障碍服务由系统常驻绑定，作为锚点帮通知监听在进程重启后重新连上。
        ListenerHealth.ensureConnected(app, app.applicationScope)
        super.onServiceConnected()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        ListenerStatus.setAccessibilityConnected(false)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        ListenerStatus.setAccessibilityConnected(false)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PayLedgerA11y"

        /** bg D5：内容变化节流 800→1500ms；时间戳只在通过信号门槛后刷新（页面切换不节流）。 */
        const val THROTTLE_MS = 1_500L

        /** bg D4：同窗口去重窗；页面切换事件不参与判定也不记录时间戳。 */
        const val WINDOW_DEDUP_MS = 3_000L
        private const val MAX_DEPTH = 6
        private const val MAX_NODES = 120
    }
}
