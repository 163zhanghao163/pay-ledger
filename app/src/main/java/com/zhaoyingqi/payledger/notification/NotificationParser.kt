package com.zhaoyingqi.payledger.notification

enum class Channel { WECHAT, ALIPAY }

/** 解析成功产出的付款信息。 */
data class ParsedPayment(
    val channel: Channel,
    val amountCents: Long,
    val counterparty: String,
    /** 只存渠道+商户的极简摘要，不保存通知原文（隐私最小化）。 */
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
        val amountCents = extractAmountCents(haystack, payKeywords) ?: return null
        val counterparty = extractMerchant(channel, haystack).orEmpty()
        return ParsedPayment(channel, amountCents, counterparty, buildSummary(channel, counterparty))
    }

    /** 渠道只按包名判定：通知标题因版本/渠道而异（微信凭证通知标题常为「微信」），不作硬性门槛。 */
    private fun resolveChannel(packageName: String): Channel? = when (packageName) {
        ParserRules.PACKAGE_WECHAT -> Channel.WECHAT
        ParserRules.PACKAGE_ALIPAY -> Channel.ALIPAY
        else -> null
    }

    /**
     * 金额提取：列出所有 ¥ 前缀 / 「元」后缀候选，排除红包/优惠/积分等紧邻修饰词，
     * 再选离付款关键词最近的一个。都缺失则放弃（避免把「11月22日」这类日期误当金额）。
     */
    internal fun extractAmountCents(text: String, payKeywords: List<String> = emptyList()): Long? {
        val candidates = findAmountCandidates(text)
        if (candidates.isEmpty()) return null
        val preferred = candidates.filterNot { isContextExcluded(text, it) }
        val pool = preferred.ifEmpty { candidates }
        return pool.minWithOrNull(
            compareBy<AmountCandidate> { nearestKeywordDistance(text, it, payKeywords) }
                .thenBy { it.start },
        )?.cents
    }

    private data class AmountCandidate(val start: Int, val end: Int, val cents: Long)

    private fun findAmountCandidates(text: String): List<AmountCandidate> {
        val out = mutableListOf<AmountCandidate>()
        for (match in ParserRules.CURRENCY_PREFIX.findAll(text)) {
            toCents(match.groupValues.getOrNull(1))?.let {
                out.add(AmountCandidate(match.range.first, match.range.last + 1, it))
            }
        }
        // 「¥20元」会同时命中前缀和后缀：前缀优先，后缀只补充不与已收集区间重叠的金额。
        for (match in ParserRules.UNIT_SUFFIX.findAll(text)) {
            val start = match.range.first
            val end = match.range.last + 1
            if (out.any { start < it.end && it.start < end }) continue
            toCents(match.groupValues.getOrNull(1))?.let { out.add(AmountCandidate(start, end, it)) }
        }
        return out
    }

    private fun toCents(raw: String?): Long? {
        val cleaned = raw?.replace(",", "")?.takeIf { it.isNotEmpty() } ?: return null
        val dot = cleaned.indexOf('.')
        val cents = if (dot < 0) {
            cleaned.toLongOrNull()?.times(100) ?: return null
        } else {
            val yuan = cleaned.substring(0, dot).toLongOrNull() ?: return null
            val fraction = cleaned.substring(dot + 1).padEnd(2, '0').take(2).toLongOrNull() ?: return null
            yuan * 100 + fraction
        }
        return cents.takeIf { it > 0 }
    }

    private fun isContextExcluded(text: String, candidate: AmountCandidate): Boolean {
        val from = (candidate.start - ParserRules.AMOUNT_BEFORE_CHARS).coerceAtLeast(0)
        val to = (candidate.end + ParserRules.AMOUNT_AFTER_CHARS).coerceAtMost(text.length)
        val context = text.substring(from, to)
        return ParserRules.AMOUNT_EXCLUDE_WORDS.any { context.contains(it) }
    }

    private fun nearestKeywordDistance(text: String, candidate: AmountCandidate, payKeywords: List<String>): Int {
        if (payKeywords.isEmpty()) return 0
        var best = Int.MAX_VALUE
        for (keyword in payKeywords) {
            var index = text.indexOf(keyword)
            while (index >= 0) {
                val keywordEnd = index + keyword.length
                val distance = when {
                    keywordEnd <= candidate.start -> candidate.start - keywordEnd
                    index >= candidate.end -> index - candidate.end
                    else -> 0
                }
                if (distance < best) best = distance
                index = text.indexOf(keyword, index + 1)
            }
        }
        return best
    }

    private fun buildSummary(channel: Channel, counterparty: String): String {
        val base = when (channel) {
            Channel.WECHAT -> "微信付款"
            Channel.ALIPAY -> "支付宝付款"
        }
        return if (counterparty.isBlank()) base else "$base · $counterparty".take(ParserRules.MAX_SUMMARY_LEN)
    }

    private fun extractMerchant(channel: Channel, text: String): String? = when (channel) {
        Channel.WECHAT -> ParserRules.MERCHANT_WECHAT.find(text)?.groupValues?.getOrNull(1)
        Channel.ALIPAY -> ParserRules.MERCHANT_ALIPAY.find(text)?.groupValues?.getOrNull(1)
    }
}
