package com.zhaoyingqi.payledger.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountCents: Long,      // 金额（分），正数
    val channel: String,        // "WECHAT" | "ALIPAY"
    val counterparty: String,   // 商户/对方，解析不到为空串
    val occurredAt: Long,       // epochMillis，取通知收到时间
    val summary: String,        // 通知原文摘要（含银行卡尾号等，便于对账）
    val note: String = "",      // 用户备注
    val createdAt: Long,        // epochMillis，入库时间
)
