package com.zhaoyingqi.payledger

import android.app.Application
import android.util.Log
import androidx.room.withTransaction
import com.zhaoyingqi.payledger.data.LedgerDatabase
import com.zhaoyingqi.payledger.data.TransactionDao
import com.zhaoyingqi.payledger.data.TransactionRecord
import com.zhaoyingqi.payledger.notification.DedupWindow
import com.zhaoyingqi.payledger.notification.ListenerHealth
import com.zhaoyingqi.payledger.notification.NotificationRingBuffer
import com.zhaoyingqi.payledger.notification.ParsedPayment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PayLedgerApplication : Application() {
    val database: LedgerDatabase by lazy { LedgerDatabase.build(this) }
    val dao: TransactionDao get() = database.transactionDao()
    val recentNotifications = NotificationRingBuffer()

    /** 与进程同生命周期：服务被回收时不会取消正在进行的入库，避免隐形漏记。 */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        scrubLegacySummariesOnce()
    }

    /** 旧版本把通知原文写进了 summary，升级后一次性清理为结构化摘要。 */
    private fun scrubLegacySummariesOnce() {
        applicationScope.launch {
            val prefs = getSharedPreferences(ListenerHealth.PREFS_NAME, MODE_PRIVATE)
            if (prefs.getBoolean(KEY_SUMMARY_SCRUBBED, false)) return@launch
            runCatching { dao.scrubLegacySummaries() }
                .onSuccess { prefs.edit().putBoolean(KEY_SUMMARY_SCRUBBED, true).apply() }
                .onFailure { Log.w(TAG, "scrub legacy summaries failed", it) }
        }
    }

    /** 入账（含 ±10 秒去重，事务内查重+插入保证双通道并发不重复）。通知监听与模拟注入共用。 */
    suspend fun savePayment(parsed: ParsedPayment, occurredAt: Long): Boolean {
        val window = DedupWindow.of(occurredAt)
        return database.withTransaction {
            if (dao.findDuplicate(parsed.channel.name, parsed.amountCents, window.first, window.last) != null) {
                return@withTransaction false
            }
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
            true
        }
    }

    companion object {
        private const val TAG = "PayLedgerApp"
        private const val KEY_SUMMARY_SCRUBBED = "summary_scrubbed_v1"
    }
}
