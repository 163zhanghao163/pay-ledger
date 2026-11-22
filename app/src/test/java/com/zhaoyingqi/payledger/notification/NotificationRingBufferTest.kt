package com.zhaoyingqi.payledger.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRingBufferTest {

    private fun sample(pkg: String = "com.tencent.mm", at: Long) = RawNotification(pkg, "t", "b", at)

    @Test fun capacity_evicts_oldest() {
        val buf = NotificationRingBuffer(capacity = 3)
        val now = System.currentTimeMillis()
        // 相对当前时间递增（都在 7 天保留期内），避免被保留策略清掉
        repeat(5) { buf.push(sample(at = now - (4 - it) * 1000)) }
        val snap = buf.snapshot()
        assertEquals(3, snap.size)
        assertEquals(listOf(now - 2000L, now - 1000L, now), snap.map { it.postedAt })
    }

    @Test fun snapshot_drops_entries_older_than_retention() {
        val buf = NotificationRingBuffer(capacity = 10)
        val now = System.currentTimeMillis()
        buf.push(sample(at = now - NotificationRingBuffer.RETENTION_MS - 1))   // 8 天前 → 清掉
        buf.push(sample(at = now - NotificationRingBuffer.RETENTION_MS + 1))  // 7 天内 → 保留
        buf.push(sample(at = now))
        val snap = buf.snapshot()
        assertEquals(2, snap.size)
        assertTrue(snap.all { it.postedAt > now - NotificationRingBuffer.RETENTION_MS })
    }

    @Test fun clear_empties_buffer() {
        val buf = NotificationRingBuffer(capacity = 3)
        buf.push(sample(at = 1))
        buf.push(sample(at = 2))
        buf.clear()
        assertTrue(buf.snapshot().isEmpty())
    }
}
