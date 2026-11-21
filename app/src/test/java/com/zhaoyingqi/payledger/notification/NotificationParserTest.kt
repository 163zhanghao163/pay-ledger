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

    @Test fun alipay_title_missing_ignored() {
        assertNull(NotificationParser.parse("com.eg.android.AlipayGphone", "通知", "付款成功 ¥20.00"))
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

    // —— 摘要与去重窗口 ——
    @Test fun summary_truncated_to_200() {
        val long = "付款成功 ¥20.00 " + "x".repeat(300)
        val r = NotificationParser.parse("com.tencent.mm", "微信支付", long)
        assertEquals(200, r?.summary?.length)
    }

    @Test fun dedup_window_boundaries() {
        // of(1000) 的 ±3 秒窗口是 [-2000, 4000]，两端含边界
        val w = DedupWindow.of(1000L)
        assertEquals(-2000L, w.first)
        assertEquals(4000L, w.last)
        assertEquals(true, w.contains(-2000L))
        assertEquals(false, w.contains(-2001L))
    }
}
