package com.zhaoyingqi.payledger

import android.app.Application
import com.zhaoyingqi.payledger.data.LedgerDatabase
import com.zhaoyingqi.payledger.data.TransactionDao
import com.zhaoyingqi.payledger.notification.NotificationRingBuffer

class PayLedgerApplication : Application() {
    val database: LedgerDatabase by lazy { LedgerDatabase.build(this) }
    val dao: TransactionDao get() = database.transactionDao()
    val recentNotifications = NotificationRingBuffer()
}
