package com.zhaoyingqi.payledger.data

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/** v07 任务 8.1：CSV 编码纯函数——普通记录、转义、空列表、金额两位小数。 */
class CsvEncoderTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val at = LocalDate.of(2026, 11, 27).atTime(14, 30, 5).atZone(zone).toInstant().toEpochMilli()

    private fun record(
        amountCents: Long = 1250,
        counterparty: String = "星巴克",
        note: String = "",
        channel: String = "WECHAT",
        type: String = TransactionRecord.TYPE_EXPENSE,
        category: String? = "餐饮",
    ) = TransactionRecord(
        amountCents = amountCents,
        channel = channel,
        counterparty = counterparty,
        occurredAt = at,
        summary = "微信付款 · $counterparty",
        note = note,
        createdAt = at,
        type = type,
        category = category,
    )

    @Test
    fun normal_record_row_matches_columns() {
        val csv = CsvEncoder.encode(listOf(record()), zone)
        val lines = csv.split("\r\n")
        // BOM + 表头
        assertEquals(
            "\uFEFF交易时间,类型,渠道,商户,分类,金额(元),来源,备注",
            lines[0],
        )
        assertEquals(
            "2026-11-27 14:30:05,EXPENSE,微信,星巴克,餐饮,12.50,NOTIFICATION,",
            lines[1],
        )
        // 结尾 CRLF：split 后最后一项为空
        assertEquals("", lines.last())
    }

    @Test
    fun fields_with_comma_quote_newline_are_escaped() {
        val csv = CsvEncoder.encode(
            listOf(record(counterparty = "A,B\"C", note = "第一行\n第二行,含逗号")),
            zone,
        )
        val line = csv.split("\r\n")[1]
        // 含逗号/引号的字段加引号，引号双写；换行替换为空格（逗号保留在引号内）
        assertEquals(
            "2026-11-27 14:30:05,EXPENSE,微信,\"A,B\"\"C\",餐饮,12.50,NOTIFICATION,\"第一行 第二行,含逗号\"",
            line,
        )
    }

    @Test
    fun empty_list_produces_header_only() {
        val csv = CsvEncoder.encode(emptyList(), zone)
        assertEquals(
            "\uFEFF交易时间,类型,渠道,商户,分类,金额(元),来源,备注\r\n",
            csv,
        )
    }

    @Test
    fun income_and_channels_and_amounts() {
        val csv = CsvEncoder.encode(
            listOf(
                record(amountCents = 5000, channel = "ALIPAY", type = TransactionRecord.TYPE_INCOME, category = null),
                record(amountCents = 5, counterparty = ""),
            ),
            zone,
        )
        val lines = csv.split("\r\n")
        assertEquals("2026-11-27 14:30:05,INCOME,支付宝,星巴克,,50.00,NOTIFICATION,", lines[1])
        assertEquals("2026-11-27 14:30:05,EXPENSE,微信,,餐饮,0.05,NOTIFICATION,", lines[2])
    }

    @Test
    fun formatYuan_two_decimals() {
        assertEquals("0.05", CsvEncoder.formatYuan(5))
        assertEquals("12.50", CsvEncoder.formatYuan(1250))
        assertEquals("12345.67", CsvEncoder.formatYuan(1_234_567))
        assertEquals("-1.20", CsvEncoder.formatYuan(-120))
    }
}
