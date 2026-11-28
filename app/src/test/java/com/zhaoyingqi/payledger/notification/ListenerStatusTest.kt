package com.zhaoyingqi.payledger.notification

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

/** v08 任务 1.1：单一监听状态流——set 后流发射新值。 */
class ListenerStatusTest {

    @Test
    fun setters_update_snapshot_value() {
        ListenerStatus.replace(false, false, false)

        ListenerStatus.setAuthorized(true)
        assertEquals(ListenerSnapshot(true, false, false), ListenerStatus.snapshot.value)

        ListenerStatus.setNotificationConnected(true)
        assertEquals(ListenerSnapshot(true, true, false), ListenerStatus.snapshot.value)

        ListenerStatus.setAccessibilityConnected(true)
        assertEquals(ListenerSnapshot(true, true, true), ListenerStatus.snapshot.value)
    }

    @Test
    fun flow_emits_each_change() = runTest {
        ListenerStatus.replace(false, false, false)
        val emissions = mutableListOf<ListenerSnapshot>()
        val job = launch { ListenerStatus.snapshot.take(3).toList(emissions) }
        yield() // 先让采集器拿到初值，再开始变更

        // StateFlow 合并连续值：每次 set 后让采集器先处理，避免两次变更合并成一次
        ListenerStatus.setNotificationConnected(true)
        yield()
        ListenerStatus.setNotificationConnected(false)

        job.join()
        assertEquals(
            listOf(
                ListenerSnapshot(false, false, false),
                ListenerSnapshot(false, true, false),
                ListenerSnapshot(false, false, false),
            ),
            emissions,
        )
    }
}
