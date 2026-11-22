package com.zhaoyingqi.payledger.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    private val _selectedMonth = MutableStateFlow(YearMonth.from(LocalDate.now(zone)))
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    /** 是否已停留在最新月份（用于禁用「下一月」按钮）。 */
    val atLatestMonth: StateFlow<Boolean> = _selectedMonth
        .map { !it.isBefore(YearMonth.from(LocalDate.now(zone))) }
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

    fun prevMonth() {
        _selectedMonth.value = _selectedMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        if (_selectedMonth.value.isBefore(YearMonth.from(LocalDate.now(zone)))) {
            _selectedMonth.value = _selectedMonth.value.plusMonths(1)
        }
    }

    fun updateNote(id: Long, note: String) = viewModelScope.launch { dao.updateNote(id, note) }

    fun delete(id: Long) = viewModelScope.launch { dao.delete(id) }

    internal fun groupByDay(records: List<TransactionRecord>): List<DayGroup> =
        records.groupBy { Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() }
            .map { (date, list) -> DayGroup(date, date.format(dayFmt), list, list.sumOf { it.amountCents }) }
            .sortedByDescending { it.date }

    private fun monthStart(ym: YearMonth): Long = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    private fun monthEnd(ym: YearMonth): Long = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    private fun startOfToday(): Long = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
    private fun startOfTomorrow(): Long = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
}
