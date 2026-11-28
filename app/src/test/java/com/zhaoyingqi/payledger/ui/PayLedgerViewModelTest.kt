package com.zhaoyingqi.payledger.ui

import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zhaoyingqi.payledger.data.CategorySum
import com.zhaoyingqi.payledger.data.LedgerDatabase
import com.zhaoyingqi.payledger.data.TransactionDao
import com.zhaoyingqi.payledger.data.TransactionRecord
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.robolectric.Shadows.shadowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 可推进的固定时区时钟：测试日期边界用。 */
class MutableClock(var nowMs: Long, private val zoneId: ZoneId) : Clock() {
    override fun getZone(): ZoneId = zoneId
    override fun withZone(zone: ZoneId): Clock = MutableClock(nowMs, zone)
    override fun instant(): Instant = Instant.ofEpochMilli(nowMs)
}

/** v06 任务 5.1/5.3：日期边界流与 ViewModel 边界行为。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PayLedgerViewModelTest {
    private lateinit var db: LedgerDatabase
    private lateinit var dao: TransactionDao
    private val zone = ZoneId.of("Asia/Shanghai")

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

    private fun record(amountCents: Long, occurredAt: Long, counterparty: String = "") =
        TransactionRecord(
            amountCents = amountCents,
            channel = "WECHAT",
            counterparty = counterparty,
            occurredAt = occurredAt,
            summary = "微信付款",
            createdAt = occurredAt,
        )

    /** 任务 5.1：固定时钟推进到次日，边界流触发一次新发射。 */
    @Test
    fun date_boundary_flow_emits_on_midnight() = runBlocking {
        val day = LocalDate.of(2026, 11, 27)
        val clock = MutableClock(day.atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli(), zone)
        val dates = mutableListOf<LocalDate>()
        val job = launch { dateBoundaryFlow(clock).collect { dates.add(it.date) } }

        withTimeout(3000) { while (dates.isEmpty()) delay(10) }
        assertEquals(day, dates[0])

        clock.nowMs += 60_000 // 23:59:59 → 次日 00:00:59
        withTimeout(3000) { while (dates.size < 2) delay(10) }
        assertEquals(LocalDate.of(2026, 11, 28), dates[1])
        job.cancel()
    }

    /** 任务 5.3：23:59:59 记的支出跨零点后今日汇总归零。 */
    @Test
    fun today_summary_switches_at_midnight() = runBlocking {
        val day = LocalDate.of(2026, 11, 27)
        dao.insert(record(100, day.atTime(23, 0).atZone(zone).toInstant().toEpochMilli()))
        val clock = MutableClock(day.atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli(), zone)
        val vm = PayLedgerViewModel(dao, clock)

        val sums = mutableListOf<Long>()
        val job = launch { vm.todayCents.collect { sums.add(it) } }
        // VM 协程跑在 Robolectric 的 Main looper（默认暂停），轮询时需要驱动它
        withTimeout(5000) {
            while (100L !in sums) {
                shadowOf(Looper.getMainLooper()).idle()
                delay(10)
            }
        }

        clock.nowMs += 60_000 // 跨零点
        // Robolectric looper 是虚拟时钟：推进约 1.5 秒让边界流的 1 秒 delay 到期
        // （不能推进整天，否则会把下一个 ~24h 的 delay 也跑起来）
        shadowOf(Looper.getMainLooper()).idle(1_500)
        withTimeout(5000) {
            while (sums.lastOrNull() != 0L) {
                shadowOf(Looper.getMainLooper()).idle()
                delay(10)
            }
        }

        job.cancel()
    }

    /** 任务 5.3：当前年份与记录年份不同（跨年查看旧记录）时日期标签含年份。 */
    @Test
    fun group_by_day_cross_year_label_contains_year() {
        val clock = MutableClock(LocalDate.of(2027, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli(), zone)
        val vm = PayLedgerViewModel(dao, clock)
        val lastYear = LocalDate.of(2026, 12, 31).atTime(20, 0).atZone(zone).toInstant().toEpochMilli()
        val groups = vm.groupByDay(listOf(record(500, lastYear)))
        assertEquals(1, groups.size)
        assertTrue("跨年日期标签应含年份", groups[0].label.contains("2026年"))

        // 同年记录不含年份
        val sameYear = LocalDate.of(2027, 1, 1).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val groupsThisYear = vm.groupByDay(listOf(record(500, sameYear)))
        assertFalse("同年标签不应含年份", groupsThisYear[0].label.contains("年"))
        assertTrue(groupsThisYear[0].label.contains("1月1日"))
    }

    /** 任务 5.3：prevMonth / nextMonth / pickMonth / nextYear 边界。 */
    @Test
    fun month_navigation_boundaries() {
        val clock = MutableClock(LocalDate.of(2026, 11, 15).atStartOfDay(zone).toInstant().toEpochMilli(), zone)
        val vm = PayLedgerViewModel(dao, clock)

        assertEquals(YearMonth.of(2026, 11), vm.selectedMonth.value)
        vm.nextMonth() // 已是最新月，不动
        assertEquals(YearMonth.of(2026, 11), vm.selectedMonth.value)

        vm.prevMonth()
        assertEquals(YearMonth.of(2026, 10), vm.selectedMonth.value)
        vm.nextMonth() // 未到最新月，可前进
        assertEquals(YearMonth.of(2026, 11), vm.selectedMonth.value)

        vm.pickMonth(YearMonth.of(2025, 3))
        assertEquals(YearMonth.of(2025, 3), vm.selectedMonth.value)
        assertEquals(2025, vm.statsYear.value)

        vm.nextYear()
        assertEquals(2026, vm.statsYear.value) // 不可超过最新年
        vm.prevYear()
        assertEquals(2025, vm.statsYear.value)
    }

    /** v07 任务 4.1：删除后列表不含该记录，撤销后恢复。 */
    @Test
    fun delete_then_undo_restores_record() = runBlocking {
        val day = LocalDate.of(2026, 11, 27)
        val id = dao.insert(record(100, day.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()))
        val clock = MutableClock(day.atTime(10, 0).atZone(zone).toInstant().toEpochMilli(), zone)
        val vm = PayLedgerViewModel(dao, clock)

        val snapshots = mutableListOf<List<DayGroup>>()
        val job = launch { vm.groups.collect { snapshots.add(it) } }
        withTimeout(5000) {
            while (snapshots.lastOrNull().isNullOrEmpty()) {
                shadowOf(Looper.getMainLooper()).idle()
                delay(10)
            }
        }

        vm.delete(id)
        withTimeout(5000) {
            while (snapshots.lastOrNull().orEmpty().isNotEmpty()) {
                shadowOf(Looper.getMainLooper()).idle()
                delay(10)
            }
        }
        assertTrue("删除后列表应为空", snapshots.last().isEmpty())

        vm.undoDelete()
        withTimeout(5000) {
            while (snapshots.lastOrNull().orEmpty().isEmpty()) {
                shadowOf(Looper.getMainLooper()).idle()
                delay(10)
            }
        }
        assertEquals(1, snapshots.last().size)
        assertEquals(100L, snapshots.last()[0].records[0].amountCents)
        job.cancel()
    }

    /** v07 任务 7.2：连续输入关键词，debounce 后只以最终关键词查询。 */
    @Test
    fun search_debounce_uses_final_keyword() = runBlocking {
        val at = LocalDate.of(2026, 11, 20).atStartOfDay(zone).toInstant().toEpochMilli()
        dao.insert(record(100, occurredAt = at, counterparty = "美团打车"))
        dao.insert(record(200, occurredAt = at, counterparty = "星巴克"))
        val clock = MutableClock(LocalDate.of(2026, 11, 21).atStartOfDay(zone).toInstant().toEpochMilli(), zone)
        val vm = PayLedgerViewModel(dao, clock)
        val job = launch { vm.searchResults.collect {} } // 激活 WhileSubscribed 流

        try {
            vm.setSearchKeyword("美团")
            // debounce(250ms) 跑在 Robolectric 虚拟 looper 上：轮询时每次推进 60ms，
            // 让 debounce 到期并驱动 Room 结果回投（不能推进整天，见跨零点用例注释）
            withTimeout(8000) {
                while (vm.searchResults.value?.records.isNullOrEmpty()) {
                    shadowOf(Looper.getMainLooper()).idle(60)
                    delay(10)
                }
            }
            assertEquals(listOf("美团打车"), vm.searchResults.value?.records?.map { it.counterparty })

            // 连续输入：debounce 合并，最终只按「星巴克」查询
            vm.setSearchKeyword("星")
            vm.setSearchKeyword("星巴")
            vm.setSearchKeyword("星巴克")
            withTimeout(8000) {
                while (vm.searchResults.value?.records?.map { it.counterparty } != listOf("星巴克")) {
                    shadowOf(Looper.getMainLooper()).idle(60)
                    delay(10)
                }
            }
            assertEquals(listOf("星巴克"), vm.searchResults.value?.records?.map { it.counterparty })
        } finally {
            job.cancel()
        }
    }

    /** v07 任务 5.4：分类占比之和为 100%，分类合计与已归类支出一致。 */
    @Test
    fun category_breakdown_percentages_sum_to_100() = runBlocking {
        val jan = LocalDate.of(2026, 1, 10).atStartOfDay(zone).toInstant().toEpochMilli()
        dao.insert(record(300, occurredAt = jan).copy(category = "餐饮"))
        dao.insert(record(100, occurredAt = jan).copy(category = "交通"))
        val clock = MutableClock(LocalDate.of(2026, 6, 1).atStartOfDay(zone).toInstant().toEpochMilli(), zone)
        val vm = PayLedgerViewModel(dao, clock)

        val cats = mutableListOf<List<CategorySum>>()
        val job = launch { vm.categorySums.collect { cats.add(it) } }
        withTimeout(5000) {
            while (cats.lastOrNull().isNullOrEmpty()) {
                shadowOf(Looper.getMainLooper()).idle()
                delay(10)
            }
        }
        // 金额降序：餐饮 300 在前
        assertEquals(listOf("餐饮", "交通"), cats.last().map { it.category })
        val sum = cats.last().sumOf { it.totalCents }
        assertEquals(400L, sum)
        // 占比之和（与 UI 相同的四舍五入口径）为 100%
        val percentSum = cats.last().sumOf { (it.totalCents * 100 / sum).toInt() }
        assertEquals(100, percentSum)
        job.cancel()
    }
}
