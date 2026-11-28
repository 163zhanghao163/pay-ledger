package com.zhaoyingqi.payledger.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * CSV 编码（v07 D8，纯函数）：UTF-8 BOM + CRLF + 引号转义 + 金额两位小数。
 * 列：交易时间、类型、渠道、商户、分类、金额(元)、来源、备注。
 * BOM 让 Windows Excel/WPS 直接双击打开不乱码；个别编辑器会多显示一个 BOM 字符，接受。
 */
object CsvEncoder {
    private val HEADERS = listOf("交易时间", "类型", "渠道", "商户", "分类", "金额(元)", "来源", "备注")
    private const val BOM = "\uFEFF"
    private val TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun encode(records: List<TransactionRecord>, zone: ZoneId = ZoneId.systemDefault()): String {
        val sb = StringBuilder()
        sb.append(BOM).append(HEADERS.joinToString(",") { escape(it) }).append("\r\n")
        records.forEach { r ->
            val time = Instant.ofEpochMilli(r.occurredAt).atZone(zone).format(TIME_FMT)
            val fields = listOf(
                time,
                r.type,
                channelLabel(r.channel),
                r.counterparty,
                r.category.orEmpty(),
                formatYuan(r.amountCents),
                r.source,
                r.note,
            )
            sb.append(fields.joinToString(",") { escape(it) }).append("\r\n")
        }
        return sb.toString()
    }

    /** 金额分 → 元字符串，固定两位小数（负数不应出现，防御性处理）。 */
    internal fun formatYuan(cents: Long): String {
        val negative = cents < 0
        val abs = if (negative) -cents else cents
        val sign = if (negative) "-" else ""
        return "$sign${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
    }

    internal fun channelLabel(channel: String): String = when (channel) {
        "WECHAT" -> "微信"
        "ALIPAY" -> "支付宝"
        else -> "其他"
    }

    /** 含逗号、引号、换行的字段加引号；引号双写转义。 */
    internal fun escape(field: String): String {
        val clean = field.replace("\r", " ").replace("\n", " ")
        return if (clean.contains(',') || clean.contains('"')) {
            "\"${clean.replace("\"", "\"\"")}\""
        } else {
            clean
        }
    }
}
