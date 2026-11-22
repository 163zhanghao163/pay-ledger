package com.zhaoyingqi.payledger.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhaoyingqi.payledger.data.MonthSum
import com.zhaoyingqi.payledger.data.TransactionDao
import com.zhaoyingqi.payledger.data.TransactionRecord

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DayGroup(
    val date: LocalDate,
    val label: String,
    val records: List<TransactionRecord>,
    val totalCents: Long,
)

@OptIn(ExperimentalCoroutinesApi::class)
class PayLedgerViewModel(private val dao: TransactionDao) : ViewModel() {

    private val zone = ZoneId.systemDefault()
    private val dayFmt = DateTimeFormatter.ofPattern("MM月dd日")

    // —— 明细页 ——
    private val _selectedMonth = MutableStateFlow(currentMonth())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    /** 是否已停留在最新月份（用于禁用「下一月」）。 */
    val atLatestMonth: StateFlow<Boolean> = _selectedMonth
        .map { !it.isBefore(currentMonth()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val groups: StateFlow<List<DayGroup>> = _selectedMonth
        .flatMapLatest { ym -> dao.observeBetween(monthStart(ym), monthEnd(ym)) }
        .map { groupByDay(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // 今日汇总固定为当前自然日；跨天后回到本页由 ON_RESUME 重建状态（MVP 取舍）。
    val todayCents: StateFlow<Long> = dao.observeSumBetween(startOfToday(), startOfTomorrow())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val monthCents: StateFlow<Long> = _selectedMonth
        .flatMapLatest { ym -> dao.observeSumBetween(monthStart(ym), monthEnd(ym)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    // —— 统计页 ——
    private val _statsYear = MutableStateFlow(currentMonth().year)
    val statsYear: StateFlow<Int> = _statsYear.asStateFlow()

    val monthlySums: StateFlow<List<MonthSum>> = _statsYear
        .flatMapLatest { y -> dao.observeMonthlySums(yearStart(y), yearEnd(y)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val latestMonth: YearMonth get() = currentMonth()
    val latestYear: Int get() = currentMonth().year

    fun prevMonth() {
        _selectedMonth.value = _selectedMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        if (_selectedMonth.value.isBefore(currentMonth())) {
            _selectedMonth.value = _selectedMonth.value.plusMonths(1)
        }
    }

    /** 从统计页跳转/月份选择弹窗：同时选中月份并同步统计年份。 */
    fun pickMonth(ym: YearMonth) {
        _selectedMonth.value = ym
        _statsYear.value = ym.year
    }

    fun prevYear() {
        _statsYear.value -= 1
    }

    fun nextYear() {
        if (_statsYear.value < latestYear) _statsYear.value += 1
    }

    /** 手动补记一笔：时间取当前时刻，渠道三选（微信/支付宝/手动）。 */
    fun addManual(amountCents: Long, channel: String, note: String) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        dao.insert(
            TransactionRecord(
                amountCents = amountCents,
                channel = channel,
                counterparty = "",
                occurredAt = now,
                summary = "手动记账",
                note = note,
                createdAt = now,
            ),
        )
    }

    fun updateRecord(id: Long, amountCents: Long, note: String) =
        viewModelScope.launch { dao.update(id, amountCents, note) }

    fun delete(id: Long) = viewModelScope.launch { dao.delete(id) }

    internal fun groupByDay(records: List<TransactionRecord>): List<DayGroup> =
        records.groupBy { Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() }
            .map { (date, list) -> DayGroup(date, date.format(dayFmt), list, list.sumOf { it.amountCents }) }
            .sortedByDescending { it.date }

    private fun currentMonth(): YearMonth = YearMonth.from(LocalDate.now(zone))
    private fun monthStart(ym: YearMonth): Long = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    private fun monthEnd(ym: YearMonth): Long = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    private fun yearStart(y: Int): Long = LocalDate.of(y, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
    private fun yearEnd(y: Int): Long = LocalDate.of(y + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
    private fun startOfToday(): Long = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
    private fun startOfTomorrow(): Long = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
}
