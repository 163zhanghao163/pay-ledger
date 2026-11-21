package com.zhaoyingqi.payledger.notification

enum class Channel { WECHAT, ALIPAY }

/** 解析成功产出的付款信息。 */
data class ParsedPayment(
    val channel: Channel,
    val amountCents: Long,
    val counterparty: String,
    val summary: String,
)

object NotificationParser {
    fun parse(packageName: String, title: String?, text: String?): ParsedPayment? = null
}
