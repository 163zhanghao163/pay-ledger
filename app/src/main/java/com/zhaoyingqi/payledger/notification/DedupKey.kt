package com.zhaoyingqi.payledger.notification

import java.security.MessageDigest

/**
 * 存储层幂等兜底键（v06 D3）：`sha256("渠道|金额分|商户|交易时间(分)")` 取前 16 位十六进制。
 * 交易时间按分钟取桶，因此 12:59:59 与 13:00:01 的同一笔会落不同桶——
 * 这是兜底而不是主判据，主判重在 [DuplicatePolicy] 的候选窗口判定。
 * 仅自动采集记录生成；手动记录 dedupKey = NULL（SQLite 唯一索引允许多个 NULL）。
 */
object DedupKey {
    fun of(channel: String, amountCents: Long, counterparty: String, transactionTimeMs: Long): String {
        val minuteBucket = transactionTimeMs / 60_000L
        val raw = "$channel|$amountCents|$counterparty|$minuteBucket"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(KEY_LEN)
    }

    private const val KEY_LEN = 16
}
