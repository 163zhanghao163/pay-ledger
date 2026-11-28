package com.zhaoyingqi.payledger.notification

/**
 * bg D4/D5：无障碍事件的时间维决策（纯逻辑，时钟由调用方传入便于 JVM 单测）。
 * 只管节流与同窗口去重两个时间窗；付款信号门槛（D2）在服务流程里由 [ParserRules.hasPaySignal] 承担。
 */
internal class A11yGate {

    private var lastHandledAt = 0L
    private val lastWindowProcessedAt = mutableMapOf<Int, Long>()

    /** 内容变化事件节流（D5：时间戳只在通过信号门槛后刷新）；页面切换不节流。 */
    fun isContentThrottled(isContentEvent: Boolean, now: Long): Boolean =
        isContentEvent && now - lastHandledAt < PaymentAccessibilityService.THROTTLE_MS

    fun refreshThrottle(now: Long) {
        lastHandledAt = now
    }

    /** 同窗口去重（D4）：[PaymentAccessibilityService.WINDOW_DEDUP_MS] 内同窗口内容变化不再处理；页面切换豁免。 */
    fun isWindowDeduped(windowId: Int, isContentEvent: Boolean, now: Long): Boolean =
        isContentEvent && now - (lastWindowProcessedAt[windowId] ?: 0L) < PaymentAccessibilityService.WINDOW_DEDUP_MS

    /** 解析成功（或完成遍历判定非付款）后记录窗口时间戳；映射超限时整体清空（去重窗只有 3 秒，代价可忽略）。 */
    fun markWindowProcessed(windowId: Int, now: Long) {
        if (lastWindowProcessedAt.size > WINDOW_MAP_MAX) lastWindowProcessedAt.clear()
        lastWindowProcessedAt[windowId] = now
    }

    companion object {
        private const val WINDOW_MAP_MAX = 64
    }
}
