package com.zhaoyingqi.payledger.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhaoyingqi.payledger.data.TransactionDao
import com.zhaoyingqi.payledger.data.TransactionRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DayGroup(
    val date: LocalDate,
    val label: String,
    val records: List<TransactionRecord>,
    val totalCents: Long,
)

class PayLedgerViewModel(private val dao: TransactionDao) : ViewModel() {

    private val zone = ZoneId.systemDefault()
    private val dayFmt = DateTimeFormatter.ofPattern("MM月dd日")

    val groups: StateFlow<List<DayGroup>> = dao.observeAll()
        .map { groupByDay(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // 汇总区间在 ViewModel 创建时计算：跨天后回到本页即由 ON_RESUME 重建状态（MVP 取舍）。
    val todayCents: StateFlow<Long> = dao.observeSumBetween(startOfToday(), startOfTomorrow())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val monthCents: StateFlow<Long> = dao.observeSumBetween(startOfMonth(), startOfNextMonth())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun updateNote(id: Long, note: String) = viewModelScope.launch { dao.updateNote(id, note) }

    fun delete(id: Long) = viewModelScope.launch { dao.delete(id) }

    internal fun groupByDay(records: List<TransactionRecord>): List<DayGroup> =
        records.groupBy { Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() }
            .map { (date, list) -> DayGroup(date, date.format(dayFmt), list, list.sumOf { it.amountCents }) }
            .sortedByDescending { it.date }

    private fun startOfToday(): Long = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
    private fun startOfTomorrow(): Long = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    private fun startOfMonth(): Long = LocalDate.now(zone).withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
    private fun startOfNextMonth(): Long = LocalDate.now(zone).plusMonths(1).withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
}
