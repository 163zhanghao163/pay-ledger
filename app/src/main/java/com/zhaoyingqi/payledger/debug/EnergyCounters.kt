package com.zhaoyingqi.payledger.debug

import java.util.concurrent.atomic.AtomicInteger

/**
 * bg D7：后台能耗计数器（进程内，调试页展示）。
 * 调用点都用 `if (BuildConfig.DEBUG)` 包裹，release 构建里常量折叠 + R8 会消除调用。
 */
object EnergyCounters {
    private val received = AtomicInteger()
    private val renders = AtomicInteger()
    private val traversals = AtomicInteger()
    private val nodesRead = AtomicInteger()
    private val shadowDiffs = AtomicInteger()

    fun onEventReceived() {
        received.incrementAndGet()
    }

    fun onRender() {
        renders.incrementAndGet()
    }

    fun onTraversal(nodeCount: Int) {
        traversals.incrementAndGet()
        nodesRead.addAndGet(nodeCount)
    }

    fun onShadowDiff() {
        shadowDiffs.incrementAndGet()
    }

    fun snapshot(): EnergySnapshot = EnergySnapshot(
        receivedEvents = received.get(),
        renders = renders.get(),
        traversals = traversals.get(),
        nodesRead = nodesRead.get(),
        shadowDiffs = shadowDiffs.get(),
    )
}

/** 五项计数的瞬时快照（bg D7）。 */
data class EnergySnapshot(
    val receivedEvents: Int,
    val renders: Int,
    val traversals: Int,
    val nodesRead: Int,
    val shadowDiffs: Int,
)
