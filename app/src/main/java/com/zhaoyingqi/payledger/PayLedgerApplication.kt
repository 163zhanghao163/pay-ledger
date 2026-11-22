package com.zhaoyingqi.payledger

import android.app.Application
import com.zhaoyingqi.payledger.data.LedgerDatabase
import com.zhaoyingqi.payledger.data.TransactionDao
import com.zhaoyingqi.payledger.data.TransactionRecord
import com.zhaoyingqi.payledger.notification.DedupWindow
import com.zhaoyingqi.payledger.notification.NotificationRingBuffer
import com.zhaoyingqi.payledger.notification.ParsedPayment

class PayLedgerApplication : Application() {
    val database: LedgerDatabase by lazy { LedgerDatabase.build(this) }
    val dao: TransactionDao get() = database.transactionDao()
    val recentNotifications = NotificationRingBuffer()

    /** 入账（含 ±3 秒去重）。通知监听与模拟注入共用；返回是否实际入库。 */
    suspend fun savePayment(parsed: ParsedPayment, occurredAt: Long): Boolean {
        val window = DedupWindow.of(occurredAt)
        if (dao.findDuplicate(parsed.channel.name, parsed.amountCents, window.first, window.last) != null) return false
        dao.insert(
            TransactionRecord(
                amountCents = parsed.amountCents,
                channel = parsed.channel.name,
                counterparty = parsed.counterparty,
                occurredAt = occurredAt,
                summary = parsed.summary,
                createdAt = System.currentTimeMillis(),
            ),
        )
        return true
    }
}
