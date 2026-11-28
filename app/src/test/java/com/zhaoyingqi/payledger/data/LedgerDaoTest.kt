package com.zhaoyingqi.payledger.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Room 内存库的 DAO 基础行为（v06 任务 1.3）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LedgerDaoTest {
    private lateinit var db: LedgerDatabase
    private lateinit var dao: TransactionDao

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val dec2026a = LocalDate.of(2026, 12, 9).atStartOfDay(zone).toInstant().toEpochMilli()
    private val dec2026b = LocalDate.of(2026, 12, 10).atStartOfDay(zone).toInstant().toEpochMilli()
    private val jan2027 = LocalDate.of(2027, 1, 10).atStartOfDay(zone).toInstant().toEpochMilli()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.transactionDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun record(
        amountCents: Long,
        occurredAt: Long = dec2026b,
        note: String = "",
        channel: String = "WECHAT",
        counterparty: String = "测试商户",
        category: String? = null,
    ) = TransactionRecord(
        amountCents = amountCents,
        channel = channel,
        counterparty = counterparty,
        occurredAt = occurredAt,
        summary = "微信付款",
        note = note,
        createdAt = occurredAt,
        category = category,
    )

    @Test
    fun insert_and_observeBetween_returns_month_records() = runTest {
        dao.insert(record(100, occurredAt = dec2026a))
        dao.insert(record(200, occurredAt = dec2026b))
        dao.insert(record(300, occurredAt = jan2027))

        val dec = dao.observeBetween(monthStart(2026, 12), monthStart(2027, 1)).first()
        assertEquals(2, dec.size)
        // 按时间倒序。
        assertEquals(200L, dec.first().amountCents)
        val decSum = dao.observeSumBetween(monthStart(2026, 12), monthStart(2027, 1)).first()
        assertEquals(300L, decSum)
    }

    @Test
    fun update_changes_amount_note_and_time() = runTest {
        val id = dao.insert(record(100, note = "旧"))
        dao.update(id, 150, "新备注", jan2027)
        val all = dao.observeBetween(0, Long.MAX_VALUE).first()
        assertEquals(1, all.size)
        assertEquals(150L, all[0].amountCents)
        assertEquals("新备注", all[0].note)
        assertEquals(jan2027, all[0].occurredAt)
    }

    @Test
    fun delete_is_soft_delete_and_hides_record() = runTest {
        val id = dao.insert(record(100))
        dao.softDelete(id, System.currentTimeMillis())
        val all = dao.observeBetween(0, Long.MAX_VALUE).first()
        assertTrue(all.isEmpty())
    }

    @Test
    fun monthlySums_group_by_local_month() = runTest {
        dao.insert(record(100, occurredAt = dec2026a))
        dao.insert(record(50, occurredAt = dec2026b))
        dao.insert(record(300, occurredAt = jan2027))
        val sums = dao.observeMonthlySums(yearStart(2026), yearStart(2027)).first()
        assertEquals(1, sums.size)
        assertEquals(12, sums[0].month)
        assertEquals(150L, sums[0].totalCents)
    }

    @Test
    fun insert_conflict_on_dedupKey_returns_minus_one() = runTest {
        dao.insert(record(100).copy(dedupKey = "abc123"))
        val second = dao.insert(record(100).copy(dedupKey = "abc123"))
        assertEquals(-1L, second)
        assertEquals(1, dao.observeBetween(0, Long.MAX_VALUE).first().size)
    }

    @Test
    fun insert_manual_records_without_dedupKey_all_kept() = runTest {
        dao.insert(record(100, channel = "MANUAL").copy(source = TransactionRecord.SOURCE_MANUAL))
        dao.insert(record(200, channel = "MANUAL").copy(source = TransactionRecord.SOURCE_MANUAL))
        assertEquals(2, dao.observeBetween(0, Long.MAX_VALUE).first().size)
    }

    // —— v07 任务 1.4/1.5：软删除可见性与清理 ——
    @Test
    fun soft_deleted_record_hidden_from_all_queries() = runTest {
        val id = dao.insert(record(100, occurredAt = dec2026a))
        dao.insert(record(50, occurredAt = dec2026b, category = "餐饮"))

        dao.softDelete(id, System.currentTimeMillis())

        // 列表不可见
        assertEquals(1, dao.observeBetween(0, Long.MAX_VALUE).first().size)
        // 汇总不可见
        assertEquals(50L, dao.observeSumBetween(0, Long.MAX_VALUE).first())
        // 月度统计不可见
        val sums = dao.observeMonthlySums(yearStart(2026), yearStart(2027)).first()
        assertEquals(1, sums.size)
        assertEquals(50L, sums[0].totalCents)
        // 分类聚合不可见
        val cats = dao.observeCategorySums("EXPENSE", 0, Long.MAX_VALUE).first()
        assertEquals(listOf("餐饮"), cats.map { it.category })
    }

    @Test
    fun restore_makes_record_visible_again() = runTest {
        val id = dao.insert(record(100))
        dao.softDelete(id, 1L)
        dao.restore(id)
        assertEquals(1, dao.observeBetween(0, Long.MAX_VALUE).first().size)
    }

    @Test
    fun purgeDeleted_physically_removes_expired_rows_only() = runTest {
        val keepId = dao.insert(record(100))
        val oldId = dao.insert(record(200))
        dao.softDelete(keepId, 10_000L)
        dao.softDelete(oldId, 5_000L)

        val purged = dao.purgeDeleted(before = 9_000L)
        assertEquals(1, purged)
        // 未过期软删除记录：列表仍隐藏
        assertEquals(0, dao.observeBetween(0, Long.MAX_VALUE).first().size)
        // 过期记录已物理不存在：判重候选不过滤已删除（D1 例外），可证明物理行的有无
        assertEquals(0, dao.findDuplicateCandidates("WECHAT", 200, 0, Long.MAX_VALUE).size)
        assertEquals(1, dao.findDuplicateCandidates("WECHAT", 100, 0, Long.MAX_VALUE).size)
    }

    @Test
    fun income_sum_tracked_separately_from_expense() = runTest {
        dao.insert(record(100, occurredAt = dec2026a).copy(type = TransactionRecord.TYPE_EXPENSE))
        dao.insert(record(5000, occurredAt = dec2026b).copy(type = TransactionRecord.TYPE_INCOME))
        assertEquals(100L, dao.observeSumBetween(0, Long.MAX_VALUE).first())
        assertEquals(5000L, dao.observeIncomeSumBetween(0, Long.MAX_VALUE).first())
    }

    @Test
    fun top_merchants_orders_by_amount_desc() = runTest {
        dao.insert(record(300, counterparty = "星巴克", occurredAt = dec2026a))
        dao.insert(record(100, counterparty = "星巴克", occurredAt = dec2026b))
        dao.insert(record(2000, counterparty = "京东", occurredAt = dec2026b))
        val top = dao.observeTopMerchants("EXPENSE", 0, Long.MAX_VALUE).first()
        assertEquals(listOf("京东", "星巴克"), top.map { it.counterparty })
        assertEquals(2000L, top[0].totalCents)
        assertEquals(400L, top[1].totalCents)
    }

    @Test
    fun dedup_candidates_include_soft_deleted_records() = runTest {
        val key = "dupkey001"
        val id = dao.insert(record(100).copy(dedupKey = key))
        dao.softDelete(id, System.currentTimeMillis())
        // 判重候选不过滤已删除记录（v07 D1 例外）
        val candidates = dao.findDuplicateCandidates("WECHAT", 100, 0, Long.MAX_VALUE)
        assertEquals(1, candidates.size)
    }

    // —— v07 任务 3.1：编辑扩展不改 source/dedupKey ——
    @Test
    fun updateFull_keeps_source_and_dedupKey() = runTest {
        val id = dao.insert(
            record(100, counterparty = "旧商户")
                .copy(source = TransactionRecord.SOURCE_ACCESSIBILITY, dedupKey = "dup-key-001", timeEstimated = true),
        )
        dao.updateFull(id, 150, "备注", jan2027, "ALIPAY", "新商户", "INCOME", "交通")

        val all = dao.observeBetween(0, Long.MAX_VALUE).first()
        assertEquals(1, all.size)
        val r = all[0]
        assertEquals(150L, r.amountCents)
        assertEquals("备注", r.note)
        assertEquals(jan2027, r.occurredAt)
        assertEquals("ALIPAY", r.channel)
        assertEquals("新商户", r.counterparty)
        assertEquals("INCOME", r.type)
        assertEquals("交通", r.category)
        // source 与 dedupKey 不在更新列，结构上保证不被改；用户确认后不再视为估算
        assertEquals(TransactionRecord.SOURCE_ACCESSIBILITY, r.source)
        assertEquals("dup-key-001", r.dedupKey)
        assertEquals(false, r.timeEstimated)
    }

    // —— v07 任务 4.4：软删除后同一 dedupKey 再插入被拒绝 ——
    @Test
    fun reinsert_same_dedupKey_after_soft_delete_rejected() = runTest {
        val id = dao.insert(record(100).copy(dedupKey = "dup-again"))
        dao.softDelete(id, System.currentTimeMillis())
        // 用户删掉的付款不会被再次采集回来：判重候选含已删除（D1 例外）+ 唯一索引兜底
        val second = dao.insert(record(100).copy(dedupKey = "dup-again"))
        assertEquals(-1L, second)
    }

    // —— v07 任务 7.1：搜索查询（关键词按 VM 契约先经 SearchQuery.build 转义）——
    @Test
    fun search_matches_counterparty_and_note() = runTest {
        dao.insert(record(100, counterparty = "星巴克", note = ""))
        dao.insert(record(200, counterparty = "便利店", note = "给同事带咖啡"))
        dao.insert(record(300, counterparty = "书店", note = ""))

        // 商户命中
        assertEquals(1, dao.search(0, Long.MAX_VALUE, null, null, null, null, null, SearchQuery.build("星巴克")).first().size)
        // 备注命中
        assertEquals(1, dao.search(0, Long.MAX_VALUE, null, null, null, null, null, SearchQuery.build("咖啡")).first().size)
        // 关键词无命中
        assertEquals(0, dao.search(0, Long.MAX_VALUE, null, null, null, null, null, SearchQuery.build("不存在")).first().size)
    }

    @Test
    fun search_combines_channel_type_and_amount_range() = runTest {
        dao.insert(record(100, channel = "WECHAT"))
        dao.insert(record(1500, channel = "WECHAT"))
        dao.insert(record(1500, channel = "ALIPAY"))

        // 渠道 + 金额区间交集：只有 WECHAT 且 ≥1000 的那条
        val rows = dao.search(0, Long.MAX_VALUE, "WECHAT", null, null, 1000L, null, null).first()
        assertEquals(1, rows.size)
        assertEquals(1500L, rows[0].amountCents)
        // 类型筛选
        assertEquals(3, dao.search(0, Long.MAX_VALUE, null, "EXPENSE", null, null, null, null).first().size)
    }

    @Test
    fun search_percent_is_literal_not_wildcard() = runTest {
        dao.insert(record(100, counterparty = "九五折95%店"))
        dao.insert(record(200, counterparty = "九五折95折扣店"))

        // 「%」按字面匹配：只命中包含「95%」字样的那条，而不是任意后缀
        val rows = dao.search(0, Long.MAX_VALUE, null, null, null, null, null, SearchQuery.build("95%")).first()
        assertEquals(1, rows.size)
        assertEquals("九五折95%店", rows[0].counterparty)
        // 纯「%」按字面查找：只命中含「%」字样的那条；若当作通配符则会命中全部 2 条
        assertEquals(1, dao.search(0, Long.MAX_VALUE, null, null, null, null, null, SearchQuery.build("%")).first().size)
    }

    private fun monthStart(year: Int, month: Int): Long =
        LocalDate.of(year, month, 1).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun yearStart(year: Int): Long =
        LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
}
