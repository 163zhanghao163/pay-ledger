package com.zhaoyingqi.payledger.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** bg 任务 2.1：付款信号判定——比解析宽松，只答「值不值得细看」。 */
class HasPaySignalTest {

    @Test
    fun pay_keyword_hits() {
        assertTrue(ParserRules.hasPaySignal("你的订单已支付成功 ¥35.00"))
        assertTrue(ParserRules.hasPaySignal("微信支付凭证：付款给 星巴克 ¥32"))
    }

    @Test
    fun currency_prefix_hits() {
        assertTrue(ParserRules.hasPaySignal("半角 ¥20"))
        assertTrue(ParserRules.hasPaySignal("全角 ￥8.80"))
    }

    @Test
    fun unit_suffix_hits() {
        assertTrue(ParserRules.hasPaySignal("你有一笔10.00元的支出"))
        assertTrue(ParserRules.hasPaySignal("付款 100 元"))
    }

    @Test
    fun no_signal_for_plain_text() {
        assertFalse(ParserRules.hasPaySignal("在吗？晚上一起吃饭"))
        assertFalse(ParserRules.hasPaySignal("订单已创建，请等待卖家发货"))
    }

    @Test
    fun blank_is_no_signal() {
        assertFalse(ParserRules.hasPaySignal(""))
        assertFalse(ParserRules.hasPaySignal("   \n "))
    }

    @Test
    fun yuan_without_number_is_not_signal() {
        // 「元旦」这类不含数字的「元」不算信号（bg D1 注释约定）
        assertFalse(ParserRules.hasPaySignal("元旦快乐，一起去跨年吗"))
    }
}
