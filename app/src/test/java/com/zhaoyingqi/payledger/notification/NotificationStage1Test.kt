package com.zhaoyingqi.payledger.notification

import org.junit.Assert.assertEquals
import org.junit.Test

/** bg 任务 2.2：通知通道第一段判定（stage1Decision）四条分支与收入豁免。 */
class NotificationStage1Test {

    @Test
    fun income_word_without_switch_drops() {
        // 收款到账在 HARD_IGNORE 里；未开启「记收入」时第一段直接丢弃，不渲染
        assertEquals(
            Stage1Decision.DROP,
            stage1Decision("微信支付\n收款到账 100.00 元", incomeEnabled = false),
        )
    }

    @Test
    fun income_word_with_switch_is_not_dropped() {
        // 开启「记收入」：收入关键词豁免硬忽略，文案含「N 元」金额 → 渲染并入，交给解析器走收入分支
        assertEquals(
            Stage1Decision.RENDER_MERGE,
            stage1Decision("微信支付\n收款到账 100.00 元", incomeEnabled = true),
        )
    }

    @Test
    fun hard_ignore_with_pay_keyword_still_renders() {
        // 硬忽略词与付款词并存（待支付订单附实付金额）：不能被第一段误杀
        assertEquals(
            Stage1Decision.RENDER_MERGE,
            stage1Decision("订单待支付，实付 ¥18.00", incomeEnabled = false),
        )
    }

    @Test
    fun blank_extras_renders_only() {
        assertEquals(Stage1Decision.RENDER_ONLY, stage1Decision("", incomeEnabled = false))
        assertEquals(Stage1Decision.RENDER_ONLY, stage1Decision("\n", incomeEnabled = false))
    }

    @Test
    fun plain_text_uses_extras_only() {
        // 有文本但无任何付款信号：只用 extras 解析，不渲染 RemoteViews
        assertEquals(
            Stage1Decision.EXTRAS_ONLY,
            stage1Decision("微信\n在吗？晚上一起吃饭", incomeEnabled = false),
        )
    }
}
