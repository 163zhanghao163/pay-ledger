package com.zhaoyingqi.payledger.notification

/** 解析规则集中定义：真实通知文案有差异时，在此迭代关键词与正则。 */
object ParserRules {
    const val PACKAGE_WECHAT = "com.tencent.mm"
    const val PACKAGE_ALIPAY = "com.eg.android.AlipayGphone"

    // 付款语义关键词（标题+文本中命中任一）。
    // 注意：微信付款凭证通知的标题常只是「微信」，正文才是凭证内容，因此渠道只按包名判定；
    // 聊天等其他通知靠 IGNORE/PAY 关键词过滤（极端情况下聊天文本含付款语义+金额会误判，取舍为宁可放宽）。
    val PAY_KEYWORDS = listOf("付款成功", "支付成功", "已成功付款", "已支付", "已付款", "已扣款", "扣款成功", "微信支付凭证", "微信支付")

    // 收款 / 非付款语义关键词（命中即忽略）
    val IGNORE_KEYWORDS = listOf("收款助手", "收款到账", "收款成功", "转账到账", "余额宝收益", "花呗还款", "退款")

    // 金额：¥ 前缀优先，其次「元」后缀；整数 8 位以内，支持千分位与最多两位小数
    val CURRENCY_PREFIX = Regex("""[¥￥]\s*([1-9]\d{0,7}(?:,\d{3})*(?:\.\d{1,2})?)""")
    val UNIT_SUFFIX = Regex("""([1-9]\d{0,7}(?:,\d{3})*(?:\.\d{1,2})?)\s*元""")

    // 商户名：宽松提取，失败置空
    val MERCHANT_WECHAT = Regex("""付款给\s*([^\s，,。]{1,30})""")
    val MERCHANT_ALIPAY = Regex("""向\s*([^\s，,。]{1,20})""")

    const val MAX_SUMMARY_LEN = 200
}

/** 去重时间窗：同渠道同金额、时间差 ≤3 秒视为重复通知。 */
object DedupWindow {
    const val WINDOW_MS = 3_000L
    fun of(occurredAt: Long): LongRange = LongRange(occurredAt - WINDOW_MS, occurredAt + WINDOW_MS)
}
