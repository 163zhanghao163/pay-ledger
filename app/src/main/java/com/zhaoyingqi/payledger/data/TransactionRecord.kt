package com.zhaoyingqi.payledger.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 记账条目（v2 结构）。
 *
 * 时间语义：[occurredAt] 是展示/统计用的交易时间（可能来自文案解析，见 timeEstimated）；
 * [capturedAt] 恒为通知到达/无障碍事件时间，是判重窗口的基准。
 * [summary] 只存结构化摘要（渠道+动作），不含通知原文等敏感内容。
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["occurredAt"]),
        Index(value = ["channel", "amountCents", "capturedAt"]),
        Index(value = ["dedupKey"], unique = true),
    ],
)
data class TransactionRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountCents: Long,      // 金额（分），正数
    val channel: String,        // "WECHAT" | "ALIPAY" | "MANUAL"
    val counterparty: String,   // 商户/对方，解析不到为空串
    val occurredAt: Long,       // epochMillis，交易时间（通知到达或文案解析）
    val summary: String,        // 结构化摘要（渠道+动作），不写通知原文
    val note: String = "",      // 用户备注
    val createdAt: Long,        // epochMillis，入库时间
    val type: String = TYPE_EXPENSE,
    val source: String = SOURCE_NOTIFICATION,
    val category: String? = null,
    val dedupKey: String? = null,
    val timeEstimated: Boolean = false,
    val capturedAt: Long = occurredAt,
    val deletedAt: Long? = null,
) {
    companion object {
        const val TYPE_EXPENSE = "EXPENSE"
        const val TYPE_INCOME = "INCOME"
        const val SOURCE_NOTIFICATION = "NOTIFICATION"
        const val SOURCE_ACCESSIBILITY = "ACCESSIBILITY"
        const val SOURCE_MANUAL = "MANUAL"
    }
}
