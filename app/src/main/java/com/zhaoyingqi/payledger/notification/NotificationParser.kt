package com.zhaoyingqi.payledger.notification

enum class Channel { WECHAT, ALIPAY }

/** 解析成功产出的付款信息。 */
data class ParsedPayment(
    val channel: Channel,
    val amountCents: Long,
    val counterparty: String,
    val summary: String,
)

/** 纯函数式解析器：无 Android 依赖，便于 JVM 单测。 */
object NotificationParser {

    /** 非付款或解析失败返回 null（宁可漏记不可错记）。无障碍通道传入 [payKeywords] = A11Y_PAY_KEYWORDS。 */
    fun parse(
        packageName: String,
        title: String?,
        text: String?,
        payKeywords: List<String> = ParserRules.PAY_KEYWORDS,
    ): ParsedPayment? {
        val t = title?.trim().orEmpty()
        val body = text?.trim().orEmpty()
        if (t.isEmpty() && body.isEmpty()) return null

        val channel = resolveChannel(packageName) ?: return null
        val haystack = "$t $body"
        if (ParserRules.IGNORE_KEYWORDS.any { haystack.contains(it) }) return null
        if (!payKeywords.any { haystack.contains(it) }) return null
        val amountCents = extractAmountCents(haystack) ?: return null
        val counterparty = extractMerchant(channel, haystack).orEmpty()
        return ParsedPayment(channel, amountCents, counterparty, haystack.take(ParserRules.MAX_SUMMARY_LEN))
    }

    /** 渠道只按包名判定：通知标题因版本/渠道而异（微信凭证通知标题常为「微信」），不作硬性门槛。 */
    private fun resolveChannel(packageName: String): Channel? = when (packageName) {
        ParserRules.PACKAGE_WECHAT -> Channel.WECHAT
        ParserRules.PACKAGE_ALIPAY -> Channel.ALIPAY
        else -> null
    }

    /** 金额提取：优先 ¥ 前缀，其次「元」后缀；都缺失则放弃（避免把「11月22日」这类日期误当金额）。 */
    internal fun extractAmountCents(text: String): Long? {
        val raw = ParserRules.CURRENCY_PREFIX.find(text)?.groupValues?.getOrNull(1)
            ?: ParserRules.UNIT_SUFFIX.find(text)?.groupValues?.getOrNull(1)
            ?: return null
        val cleaned = raw.replace(",", "")
        val dot = cleaned.indexOf('.')
        val cents = if (dot < 0) {
            cleaned.toLong() * 100
        } else {
            cleaned.substring(0, dot).toLong() * 100 +
                cleaned.substring(dot + 1).padEnd(2, '0').take(2).toLong()
        }
        return if (cents > 0) cents else null
    }

    private fun extractMerchant(channel: Channel, text: String): String? = when (channel) {
        Channel.WECHAT -> ParserRules.MERCHANT_WECHAT.find(text)?.groupValues?.getOrNull(1)
        Channel.ALIPAY -> ParserRules.MERCHANT_ALIPAY.find(text)?.groupValues?.getOrNull(1)
    }
}
