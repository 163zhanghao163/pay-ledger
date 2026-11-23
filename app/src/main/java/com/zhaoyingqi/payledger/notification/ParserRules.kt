package com.zhaoyingqi.payledger.notification

/** 解析规则集中定义：真实通知文案有差异时，在此迭代关键词与正则。 */
object ParserRules {
    const val PACKAGE_WECHAT = "com.tencent.mm"
    const val PACKAGE_ALIPAY = "com.eg.android.AlipayGphone"

    // 付款语义关键词（标题+文本中命中任一）。
    // 注意：微信付款凭证通知的标题常只是「微信」，正文才是凭证内容，因此渠道只按包名判定；
    // 聊天等其他通知靠 IGNORE/PAY 关键词过滤（极端情况下聊天文本含付款语义+金额会误判，取舍为宁可放宽）。
    // 「的支出」来自支付宝交易提醒真实格式：你有一笔10.00元的支出，点击领取10个支付宝积分。
    // 「微信支付」不作为关键词：它是支付按钮/营销文案，会连页面金额一起误判。
    val PAY_KEYWORDS = listOf(
        "付款成功", "支付成功", "已成功付款", "已支付", "已付款", "已扣款", "扣款成功",
        "微信支付凭证", "的支出",
    )

    // 无障碍通道专用：屏幕文本混有支付按钮、订单列表（历史状态「已支付」）、待支付金额等静态内容，
    // 必须比通知通道更严格——只认「成功/扣款」语义，排除订单列表常见的历史状态词。
    val A11Y_PAY_KEYWORDS = listOf("付款成功", "支付成功", "已扣款", "扣款成功", "微信支付凭证")

    // 收款 / 非付款语义关键词（命中即忽略）。待支付/待付款类订单与提醒不是支出，一并忽略。
    // 注意：「退款」不再整条忽略——纯退款通知不含付款成功语义，会被付款关键词门槛挡住；
    // 付款成功文案里附带「退款」字样（营销/售后说明）时仍应入账。
    val IGNORE_KEYWORDS = listOf(
        "收款助手", "收款到账", "收款成功", "转账到账", "余额宝收益", "花呗还款",
        "待支付", "待付款", "需支付", "应付", "去支付",
    )

    // 金额候选排除：红包/优惠/积分等金额通常不是实付金额。只检查金额两侧很短的上下文，
    // 避免「付款成功 ¥20，使用红包抵扣 ¥5」把实付金额 ¥20 也排除（红包在 ¥5 前、离 ¥20 较远）。
    val AMOUNT_EXCLUDE_WORDS = listOf("红包", "优惠", "立减", "返现", "积分", "抵扣", "代金券", "券")

    // 金额候选与上下文检查窗口：只在金额紧邻的位置判断修饰词。
    const val AMOUNT_BEFORE_CHARS = 4
    const val AMOUNT_AFTER_CHARS = 4

    // 金额：¥ 前缀优先，其次「元」后缀；整数 8 位以内，支持千分位、最多两位小数、低于 1 元（0.50元）
    val CURRENCY_PREFIX = Regex("""[¥￥]\s*((?:[1-9]\d{0,7}(?:,\d{3})*|0)(?:\.\d{1,2})?)""")
    val UNIT_SUFFIX = Regex("""((?:[1-9]\d{0,7}(?:,\d{3})*|0)(?:\.\d{1,2})?)\s*元""")

    // 商户名：宽松提取，失败置空
    val MERCHANT_WECHAT = Regex("""付款给\s*([^\s，,。]{1,30})""")
    val MERCHANT_ALIPAY = Regex("""向\s*([^\s，,。]{1,20})""")

    const val MAX_SUMMARY_LEN = 200
}

/** 去重时间窗：同渠道同金额、时间差 ≤10 秒视为重复。窗口放宽到 10 秒：通知与无障碍双通道捕获同一笔支付的时间差可能超过 3 秒。 */
object DedupWindow {
    const val WINDOW_MS = 10_000L
    fun of(occurredAt: Long): LongRange = LongRange(occurredAt - WINDOW_MS, occurredAt + WINDOW_MS)
}
