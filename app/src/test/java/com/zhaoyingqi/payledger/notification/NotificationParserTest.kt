package com.zhaoyingqi.payledger.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationParserTest {

    // —— 命中：微信付款 ——
    @Test fun wechat_voucher_style() {
        val r = NotificationParser.parse("com.tencent.mm", "微信支付", "微信支付凭证\n¥20.00\n付款成功")
        assertEquals(2000L, r?.amountCents)
        assertEquals(Channel.WECHAT, r?.channel)
    }

    @Test fun wechat_merchant_extracted() {
        val r = NotificationParser.parse("com.tencent.mm", "微信支付", "你已成功付款 ¥35.50\n付款给 星巴克咖啡")
        assertEquals(3550L, r?.amountCents)
        assertEquals("星巴克咖啡", r?.counterparty)
    }

    @Test fun wechat_unit_suffix_amount() {
        val r = NotificationParser.parse("com.tencent.mm", "微信支付", "已扣款 128.00元 宁波银行(尾号1234)")
        assertEquals(12800L, r?.amountCents)
    }

    @Test fun thousands_separator() {
        val r = NotificationParser.parse("com.tencent.mm", "微信支付", "付款成功 ¥1,234.56")
        assertEquals(123456L, r?.amountCents)
    }

    @Test fun integer_amount() {
        val r = NotificationParser.parse("com.tencent.mm", "微信支付", "已支付¥20")
        assertEquals(2000L, r?.amountCents)
    }

    // —— 命中：支付宝付款 ——
    @Test fun alipay_payment_parsed() {
        val r = NotificationParser.parse("com.eg.android.AlipayGphone", "支付宝通知", "付款成功 ¥35.50 向 星巴克")
        assertEquals(3550L, r?.amountCents)
        assertEquals(Channel.ALIPAY, r?.channel)
        assertEquals("星巴克", r?.counterparty)
    }

    @Test fun alipay_bank_deduction() {
        val r = NotificationParser.parse("com.eg.android.AlipayGphone", "支付宝通知", "已扣款 128.00元 宁波银行(尾号6688)")
        assertEquals(12800L, r?.amountCents)
    }

    // 真机截图实测格式：标题「交易提醒」+「你有一笔10.00元的支出，点击领取10个支付宝积分。」
    @Test fun alipay_transaction_reminder() {
        val r = NotificationParser.parse(
            "com.eg.android.AlipayGphone",
            "交易提醒",
            "你有一笔10.00元的支出，点击领取10个支付宝积分。",
        )
        assertEquals(1000L, r?.amountCents)
        assertEquals(Channel.ALIPAY, r?.channel)
    }

    @Test fun alipay_income_reminder_not_recorded() {
        assertNull(NotificationParser.parse("com.eg.android.AlipayGphone", "交易提醒", "你有一笔100.00元的收入，已到账。"))
    }

    @Test fun sub_yuan_amount() {
        val r = NotificationParser.parse("com.tencent.mm", "微信支付", "付款成功 ¥0.50")
        assertEquals(50L, r?.amountCents)
    }

    @Test fun sub_yuan_suffix_amount() {
        val r = NotificationParser.parse("com.eg.android.AlipayGphone", "交易提醒", "你有一笔0.99元的支出。")
        assertEquals(99L, r?.amountCents)
    }

    // —— 忽略：收款 / 非付款 ——
    @Test fun wechat_income_ignored() {
        assertNull(NotificationParser.parse("com.tencent.mm", "微信支付", "收款到账12.00元"))
    }

    @Test fun wechat_chat_message_ignored() {
        assertNull(NotificationParser.parse("com.tencent.mm", "张三", "我刚支付成功了"))
    }

    @Test fun alipay_yield_ignored() {
        assertNull(NotificationParser.parse("com.eg.android.AlipayGphone", "支付宝通知", "余额宝收益 0.12元"))
    }

    @Test fun alipay_transfer_in_ignored() {
        assertNull(NotificationParser.parse("com.eg.android.AlipayGphone", "支付宝通知", "转账到账 500.00元"))
    }

    // 渠道只按包名判定：通知标题不作硬性门槛（微信凭证通知标题常为「微信」）
    @Test fun wechat_plain_title_parsed() {
        val r = NotificationParser.parse("com.tencent.mm", "微信", "微信支付凭证\n付款成功 ￥12.34")
        assertEquals(1234L, r?.amountCents)
        assertEquals(Channel.WECHAT, r?.channel)
    }

    @Test fun alipay_plain_title_parsed() {
        val r = NotificationParser.parse("com.eg.android.AlipayGphone", "通知", "付款成功 ¥20.00")
        assertEquals(2000L, r?.amountCents)
        assertEquals(Channel.ALIPAY, r?.channel)
    }

    // —— 忽略：金额异常 / 无关输入 ——
    @Test fun zero_amount_ignored() {
        assertNull(NotificationParser.parse("com.tencent.mm", "微信支付", "付款成功 ¥0.00"))
    }

    @Test fun no_amount_ignored() {
        assertNull(NotificationParser.parse("com.tencent.mm", "微信支付", "付款成功"))
    }

    @Test fun empty_text_ignored() {
        assertNull(NotificationParser.parse("com.tencent.mm", "", ""))
    }

    @Test fun unrelated_package_ignored() {
        assertNull(NotificationParser.parse("com.android.systemui", "通知", "付款成功 ¥20.00"))
    }

    // —— 摘要（只存结构化信息，不落通知原文）与去重窗口 ——
    @Test fun summary_contains_no_raw_notification_text() {
        val r = NotificationParser.parse("com.tencent.mm", "微信支付", "付款成功 ¥20.00 宁波银行(尾号1234)")
        assertEquals("微信付款", r?.summary)
        assertEquals(false, r?.summary?.contains("宁波银行"))
    }

    @Test fun summary_keeps_structured_counterparty() {
        val r = NotificationParser.parse("com.tencent.mm", "微信支付", "付款成功 ¥20.00 付款给 星巴克")
        assertEquals("微信付款 · 星巴克", r?.summary)
    }

    @Test fun dedup_window_boundaries() {
        // of(1000) 的 ±10 秒窗口是 [-9000, 11000]，两端含边界（放宽为 10s：通知与无障碍双通道捕获有时间差）
        val w = DedupWindow.of(1000L)
        assertEquals(-9000L, w.first)
        assertEquals(11000L, w.last)
        assertEquals(true, w.contains(-9000L))
        assertEquals(false, w.contains(-9001L))
    }

    // —— 真机实测格式与误判防护 ——
    @Test fun wechat_real_lockscreen_format() {
        // 锁屏通知真实格式：标题「微信支付」正文「已支付¥11.00」
        val r = NotificationParser.parse("com.tencent.mm", "微信支付", "已支付¥11.00")
        assertEquals(1100L, r?.amountCents)
        assertEquals(Channel.WECHAT, r?.channel)
    }

    @Test fun pending_payment_not_recorded() {
        assertNull(NotificationParser.parse("com.eg.android.AlipayGphone", "交易提醒", "你有一笔待支付订单，金额¥30.00，请及时支付。"))
    }

    // —— 无障碍通道：严格关键词（按钮/订单列表静态内容不误判） ——
    @Test fun a11y_pay_button_not_recorded() {
        // 页面上「微信支付」按钮 + 任意金额：无「成功/扣款」语义，不记账
        assertNull(
            NotificationParser.parse(
                "com.tencent.mm", null, "选择支付方式 微信支付 ¥30.00", ParserRules.A11Y_PAY_KEYWORDS,
            ),
        )
    }

    @Test fun a11y_order_list_history_status_not_recorded() {
        // 订单列表里的历史状态「已支付」不是本次付款
        assertNull(
            NotificationParser.parse(
                "com.eg.android.AlipayGphone", null, "订单列表 待支付 ¥30.00 已支付 ¥11.00", ParserRules.A11Y_PAY_KEYWORDS,
            ),
        )
    }

    @Test fun a11y_success_page_recorded() {
        val r = NotificationParser.parse(
            "com.tencent.mm", null, "支付成功 ¥11.00 微信支付", ParserRules.A11Y_PAY_KEYWORDS,
        )
        assertEquals(1100L, r?.amountCents)
        assertEquals(Channel.WECHAT, r?.channel)
    }

    // —— 金额歧义：优先实付金额，排除红包/优惠 ——
    @Test fun amount_near_payment_keyword_wins() {
        val r = NotificationParser.parse(
            "com.tencent.mm", "微信支付", "订单金额 ¥99.00 优惠券 ¥10.00 付款成功 ¥30.00",
        )
        assertEquals(3000L, r?.amountCents)
    }

    @Test fun red_packet_amount_excluded() {
        val r = NotificationParser.parse("com.tencent.mm", "微信支付", "付款成功 ¥50.00，获得 ¥5.00 红包")
        assertEquals(5000L, r?.amountCents)
    }

    @Test fun red_packet_deduction_uses_paid_amount() {
        val r = NotificationParser.parse("com.tencent.mm", "微信支付", "付款成功 ¥20.00 使用红包抵扣 ¥5.00")
        assertEquals(2000L, r?.amountCents)
    }

    // —— 退款：纯退款不记账，付款文案附带退款说明仍记账 ——
    @Test fun pure_refund_not_recorded() {
        assertNull(NotificationParser.parse("com.tencent.mm", "微信支付", "退款成功 ¥20.00 已原路退回"))
    }

    @Test fun payment_with_refund_note_recorded() {
        val r = NotificationParser.parse("com.tencent.mm", "微信支付", "付款成功 ¥20.00，如不满意可申请退款")
        assertEquals(2000L, r?.amountCents)
    }
}
