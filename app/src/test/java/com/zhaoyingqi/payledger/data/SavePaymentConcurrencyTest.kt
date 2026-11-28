package com.zhaoyingqi.payledger.data

import androidx.test.core.app.ApplicationProvider
import com.zhaoyingqi.payledger.PayLedgerApplication
import com.zhaoyingqi.payledger.notification.Channel
import com.zhaoyingqi.payledger.notification.ParsedPayment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v06 任务 3.3：并发判重——同一笔并发提交只落 1 条；不同笔各落 1 条。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SavePaymentConcurrencyTest {
    private val capturedAt = 1_764_252_000_000L

    @Test
    fun concurrent_same_payment_records_once() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<PayLedgerApplication>()
        val parsed = ParsedPayment(Channel.WECHAT, 999, "测试商户", "微信付款 · 测试商户")

        val results = (1..100).map {
            async(Dispatchers.IO) { app.savePayment(parsed, capturedAt) }
        }.awaitAll()

        assertEquals(1, app.dao.observeBetween(0, Long.MAX_VALUE).first().size)
        assertEquals(1, results.count { it })
    }

    @Test
    fun concurrent_distinct_payments_all_recorded() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<PayLedgerApplication>()
        val results = (1..100).map { i ->
            async(Dispatchers.IO) {
                val parsed = ParsedPayment(Channel.WECHAT, i * 100L, "商户$i", "微信付款 · 商户$i")
                app.savePayment(parsed, capturedAt)
            }
        }.awaitAll()

        assertEquals(100, app.dao.observeBetween(0, Long.MAX_VALUE).first().size)
        assertEquals(100, results.count { it })
    }

    /** v07 任务 5.2/6.2：入账按预置规则归类；收入类型随 ParsedPayment 写库。 */
    @Test
    fun savePayment_categorizes_and_writes_type() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<PayLedgerApplication>()
        // 「星巴克」是预置规则关键词（餐饮）
        assertTrue(
            app.savePayment(
                ParsedPayment(Channel.WECHAT, 3500, "星巴克", "微信付款 · 星巴克"),
                capturedAt + 1,
            ),
        )
        val expense = app.dao.observeBetween(0, Long.MAX_VALUE).first().single { it.amountCents == 3500L }
        assertEquals("餐饮", expense.category)
        assertEquals(TransactionRecord.TYPE_EXPENSE, expense.type)

        // 收入：ParsedPayment.type = INCOME，不参与归类（收入关键词与预置商户规则不冲突）
        assertTrue(
            app.savePayment(
                ParsedPayment(Channel.WECHAT, 8000, "张三", "微信收款", type = TransactionRecord.TYPE_INCOME),
                capturedAt + 2,
            ),
        )
        val income = app.dao.observeBetween(0, Long.MAX_VALUE).first().single { it.amountCents == 8000L }
        assertEquals(TransactionRecord.TYPE_INCOME, income.type)
    }
}
