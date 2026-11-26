package com.zhaoyingqi.payledger.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ManualRecordTest {

    @Test fun manual_record_keeps_selected_occurred_at() {
        val selected = 1700000000000L
        val record = buildManualRecord(
            amountCents = 1234,
            channel = "MANUAL",
            note = "补记",
            occurredAt = selected,
            createdAt = 999L,
        )
        assertEquals(selected, record.occurredAt)
        assertEquals(999L, record.createdAt)
        assertEquals("手动记账", record.summary)
    }
}
