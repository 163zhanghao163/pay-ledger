package com.zhaoyingqi.payledger.notification

import com.zhaoyingqi.payledger.data.TransactionRecord
import java.time.ZoneId

enum class Channel { WECHAT, ALIPAY }

/** 解析成功产出的付款信息。 */
data class ParsedPayment(
    val channel: Channel,
    val amountCents: Long,
    val counterparty: String,
    /** 只存渠道+商户的极简摘要，不保存通知原文（隐私最小化）。 */
    val summary: String,
    /** 文案解析出的交易时间；解析不到或不可信为 null，入账时回退到达时间。 */
    val occurredAt: Long? = null,
    /** 交易时间为估算（文案无时间、超信任区间，或未提供到达时间）。 */
    val timeEstimated: Boolean = true,
    /** 支出 / 收入（v07 D6）。 */
    val type: String = TransactionRecord.TYPE_EXPENSE,
)

/** 纯函数式解析器：无 Android 依赖，便于 JVM 单测。 */
object NotificationParser {

    /**
     * 非付款或解析失败返回 null（宁可漏记不可错记）。无障碍通道传入 [payKeywords] = A11Y_PAY_KEYWORDS。
     * [receivedAt] 是通知到达/事件时间（交易时间解析与信任区间的基准）；不提供则不解析时间。
     * [incomeEnabled] 开启后命中收入关键词且含金额的文案解析为收入（v07 D6）。
     */
    fun parse(
        packageName: String,
        title: String?,
        text: String?,
        payKeywords: List<String> = ParserRules.PAY_KEYWORDS,
        receivedAt: Long = -1L,
        zone: ZoneId = ZoneId.systemDefault(),
        incomeEnabled: Boolean = false,
    ): ParsedPayment? {
        val t = title?.trim().orEmpty()
        val body = text?.trim().orEmpty()
        if (t.isEmpty() && body.isEmpty()) return null

        val channel = resolveChannel(packageName) ?: return null
        val haystack = "$t $body"

        // 收入分支（v07 D6）：先于硬忽略词（收款到账等词本身在 HARD_IGNORE 里）；
        // 未开启开关时收款类文案照旧被硬忽略词丢弃，行为与 v06 完全一致。
        if (incomeEnabled && ParserRules.INCOME_KEYWORDS.any { haystack.contains(it) }) {
            val amount = extractAmountCents(haystack, ParserRules.INCOME_KEYWORDS) ?: return null
            return buildParsed(channel, haystack, amount, receivedAt, zone, TransactionRecord.TYPE_INCOME)
        }

        // 硬忽略词命中即丢弃；软忽略词只在无付款关键词时丢弃（账单文案可能同时含「应付」与「实付」）。
        if (ParserRules.HARD_IGNORE.any { haystack.contains(it) }) return null
        if (!payKeywords.any { haystack.contains(it) }) return null
        val amountCents = extractAmountCents(haystack, payKeywords) ?: return null
        return buildParsed(channel, haystack, amountCents, receivedAt, zone, TransactionRecord.TYPE_EXPENSE)
    }

    private fun buildParsed(
        channel: Channel,
        haystack: String,
        amountCents: Long,
        receivedAt: Long,
        zone: ZoneId,
        type: String,
    ): ParsedPayment {
        val counterparty = extractMerchant(channel, haystack).orEmpty()
        val parsedAt = if (receivedAt > 0) PaymentTimeParser.parse(haystack, receivedAt, zone) else null
        return ParsedPayment(
            channel = channel,
            amountCents = amountCents,
            counterparty = counterparty,
            summary = buildSummary(channel, counterparty, type),
            occurredAt = parsedAt,
            timeEstimated = parsedAt == null,
            type = type,
        )
    }

    /** 渠道只按包名判定：通知标题因版本/渠道而异（微信凭证通知标题常为「微信」），不作硬性门槛。 */
    private fun resolveChannel(packageName: String): Channel? = when (packageName) {
        ParserRules.PACKAGE_WECHAT -> Channel.WECHAT
        ParserRules.PACKAGE_ALIPAY -> Channel.ALIPAY
        else -> null
    }

    /**
     * 金额提取：列出所有 ¥ 前缀 / 「元」后缀候选，排除红包/优惠/积分等紧邻修饰词，
     * 再选离付款关键词最近的一个；距离并列时优先取位于关键词**之后**的金额
     * （实付金额通常紧跟「实付/付款成功」，如「应付金额 ¥20 实付 ¥18」应取 18）。
     * 都缺失则放弃（避免把「11月22日」这类日期误当金额）。
     */
    internal fun extractAmountCents(text: String, payKeywords: List<String> = emptyList()): Long? {
        val candidates = findAmountCandidates(text)
        if (candidates.isEmpty()) return null
        val preferred = candidates.filterNot { isContextExcluded(text, it) }
        val pool = preferred.ifEmpty { candidates }
        val scored = pool.map { it to keywordProximity(text, it, payKeywords) }
        return scored.minWithOrNull(
            compareBy({ it.second.distance }, { it.second.beforeKeyword }, { it.first.start }),
        )?.first?.cents
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

    /** 金额与付款关键词的距离；[beforeKeyword] 表示金额位于关键词之前（距离并列时排在后面）。 */
    private data class KeywordProximity(val distance: Int, val beforeKeyword: Boolean)

    private fun keywordProximity(text: String, candidate: AmountCandidate, payKeywords: List<String>): KeywordProximity {
        if (payKeywords.isEmpty()) return KeywordProximity(0, false)
        var best = KeywordProximity(Int.MAX_VALUE, true)
        for (keyword in payKeywords) {
            var index = text.indexOf(keyword)
            while (index >= 0) {
                val keywordEnd = index + keyword.length
                val (distance, before) = when {
                    keywordEnd <= candidate.start -> candidate.start - keywordEnd to false
                    index >= candidate.end -> index - candidate.end to true
                    else -> 0 to false
                }
                if (distance < best.distance || (distance == best.distance && !before && best.beforeKeyword)) {
                    best = KeywordProximity(distance, before)
                }
                index = text.indexOf(keyword, index + 1)
            }
        }
        return best
    }

    private fun buildSummary(channel: Channel, counterparty: String, type: String = TransactionRecord.TYPE_EXPENSE): String {
        val base = when (channel) {
            Channel.WECHAT -> if (type == TransactionRecord.TYPE_INCOME) "微信收款" else "微信付款"
            Channel.ALIPAY -> if (type == TransactionRecord.TYPE_INCOME) "支付宝收款" else "支付宝付款"
        }
        return if (counterparty.isBlank()) base else "$base · $counterparty".take(ParserRules.MAX_SUMMARY_LEN)
    }

    private fun extractMerchant(channel: Channel, text: String): String? = when (channel) {
        Channel.WECHAT -> ParserRules.MERCHANT_WECHAT.find(text)?.groupValues?.getOrNull(1)
        Channel.ALIPAY -> ParserRules.MERCHANT_ALIPAY.find(text)?.groupValues?.getOrNull(1)
    }
}
