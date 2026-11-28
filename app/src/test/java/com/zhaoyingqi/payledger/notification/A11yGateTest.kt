package com.zhaoyingqi.payledger.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** bg 任务 3.2/3.4：无障碍事件的时间维决策——节流 1500ms、同窗口去重 3s、页面切换豁免。 */
class A11yGateTest {
    private val gate = A11yGate()
    private val t0 = 1_764_000_000_000L // 任意基准时间

    @Test
    fun throttle_window_constants() {
        // bg D5：参数集中为常量，真机样本可再调
        assertEquals(1_500L, PaymentAccessibilityService.THROTTLE_MS)
        assertEquals(3_000L, PaymentAccessibilityService.WINDOW_DEDUP_MS)
    }

    @Test
    fun content_event_within_throttle_is_blocked() {
        gate.refreshThrottle(t0)
        assertTrue(gate.isContentThrottled(isContentEvent = true, now = t0 + PaymentAccessibilityService.THROTTLE_MS - 1))
    }

    @Test
    fun content_event_after_throttle_passes() {
        gate.refreshThrottle(t0)
        assertFalse(gate.isContentThrottled(isContentEvent = true, now = t0 + PaymentAccessibilityService.THROTTLE_MS))
    }

    @Test
    fun window_state_event_never_throttled() {
        // 页面切换（STATE_CHANGED）不节流：刚处理完一个内容事件也立即放行
        gate.refreshThrottle(t0)
        assertFalse(gate.isContentThrottled(isContentEvent = false, now = t0 + 1))
    }

    @Test
    fun same_window_content_change_deduped_within_window() {
        gate.markWindowProcessed(windowId = 7, now = t0)
        assertTrue(gate.isWindowDeduped(windowId = 7, isContentEvent = true, now = t0 + 2_999))
        assertFalse(gate.isWindowDeduped(windowId = 7, isContentEvent = true, now = t0 + 3_000))
    }

    @Test
    fun page_switch_not_deduped() {
        // 页面切换事件不受同窗口去重限制（即使刚处理过该窗口的内容变化）
        gate.markWindowProcessed(windowId = 7, now = t0)
        assertFalse(gate.isWindowDeduped(windowId = 7, isContentEvent = false, now = t0 + 100))
    }

    @Test
    fun other_window_not_affected() {
        gate.markWindowProcessed(windowId = 7, now = t0)
        assertFalse(gate.isWindowDeduped(windowId = 8, isContentEvent = true, now = t0 + 100))
    }

    @Test
    fun unmarked_window_not_deduped() {
        assertFalse(gate.isWindowDeduped(windowId = 9, isContentEvent = true, now = t0))
    }

    @Test
    fun window_map_overflow_clears_old_entries() {
        // 超过映射上限整体清空：去重窗只有 3 秒，清空代价可忽略
        repeat(70) { gate.markWindowProcessed(windowId = it + 1, now = t0) }
        assertFalse(gate.isWindowDeduped(windowId = 1, isContentEvent = true, now = t0 + 100))
    }

    @Test
    fun refresh_throttle_rearms_window() {
        // 时间戳只在通过信号门槛后刷新（服务层保证）；刷新后节流窗重新起算
        gate.refreshThrottle(t0)
        assertTrue(gate.isContentThrottled(isContentEvent = true, now = t0 + 500))
        gate.refreshThrottle(t0 + 2_000)
        assertTrue(gate.isContentThrottled(isContentEvent = true, now = t0 + 2_001))
        assertFalse(
            gate.isContentThrottled(
                isContentEvent = true,
                now = t0 + 2_000 + PaymentAccessibilityService.THROTTLE_MS,
            ),
        )
    }
}
