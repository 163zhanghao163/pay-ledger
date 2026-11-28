package com.zhaoyingqi.payledger.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhaoyingqi.payledger.data.CategoryRule
import com.zhaoyingqi.payledger.data.CategoryRuleDao
import com.zhaoyingqi.payledger.data.CategorySum
import com.zhaoyingqi.payledger.data.MerchantSum
import com.zhaoyingqi.payledger.data.MonthSum
import com.zhaoyingqi.payledger.data.SearchQuery
import com.zhaoyingqi.payledger.data.TransactionDao
import com.zhaoyingqi.payledger.data.TransactionRecord
import com.zhaoyingqi.payledger.debug.timed
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DayGroup(
    val date: LocalDate,
    val label: String,
    val records: List<TransactionRecord>,
    val totalCents: Long,
)

/** 搜索筛选条件（v07 D2）：与 DAO 的可空参数一一对应。 */
data class SearchFilter(
    val channel: String? = null,
    val type: String? = null,
    val category: String? = null,
    val minCents: Long? = null,
    val maxCents: Long? = null,
) {
    /** 关键词之外是否还有生效条件（决定搜索态是否激活）。 */
    val isActive: Boolean
        get() = channel != null || type != null || category != null || minCents != null || maxCents != null
}

/** 搜索结果与超限标记（v07 D2：上限 500，超出提示缩小范围）。 */
data class SearchResult(
    val records: List<TransactionRecord>,
    val truncated: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class PayLedgerViewModel(
    private val dao: TransactionDao,
    private val clock: Clock = Clock.systemDefaultZone(),
    /** 应用上下文：注册时区变化广播用；纯 JVM 测试传 null。 */
    appContext: Context? = null,
    /** 分类规则 DAO：手动新增默认分类与规则页数据源；传 null 时不提供规则功能。 */
    private val ruleDao: CategoryRuleDao? = null,
) : ViewModel() {

    private val zone: ZoneId get() = clock.zone
    private val dayFmt = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)
    private val dayFmtWithYear = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA)

    /** 日期/时区边界：午夜与系统时区变化时重新发射，驱动所有「今天」相关的查询。 */
    private val dateBoundary = dateBoundaryFlow(clock, appContext)

    // —— 明细页 ——
    private val _selectedMonth = MutableStateFlow(currentMonth())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    /** 是否已停留在最新月份（用于禁用「下一月」）。随日期边界刷新。 */
    val atLatestMonth: StateFlow<Boolean> = combine(_selectedMonth, dateBoundary) { ym, dz ->
        !ym.isBefore(YearMonth.from(dz.date))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val groups: StateFlow<List<DayGroup>> = combine(_selectedMonth, dateBoundary) { ym, dz -> ym to dz }
        .flatMapLatest { (ym, dz) ->
            dao.observeBetween(monthStart(ym, dz.zone), monthEnd(ym, dz.zone))
                .map { groupByDay(it, dz.zone) }
                .timed("records.groups") { it.size }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val todayCents: StateFlow<Long> = dateBoundary
        .flatMapLatest { dz ->
            dao.observeSumBetween(startOfDay(dz.date, dz.zone), startOfDay(dz.date.plusDays(1), dz.zone))
                .timed("records.todaySum")
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val monthCents: StateFlow<Long> = combine(_selectedMonth, dateBoundary) { ym, dz -> ym to dz }
        .flatMapLatest { (ym, dz) ->
            dao.observeSumBetween(monthStart(ym, dz.zone), monthEnd(ym, dz.zone))
                .timed("stats.monthSum")
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** 本月收入（v07 6.4）。 */
    val monthIncomeCents: StateFlow<Long> = combine(_selectedMonth, dateBoundary) { ym, dz -> ym to dz }
        .flatMapLatest { (ym, dz) ->
            dao.observeIncomeSumBetween(monthStart(ym, dz.zone), monthEnd(ym, dz.zone))
                .timed("stats.monthIncomeSum")
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** 当前日期（预算进度跨零点重算用，v08 4.5）。 */
    val today: StateFlow<LocalDate> = dateBoundary.map { it.date }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalDate.now(clock))

    /** 当前自然月支出合计（预算进度用，区别于随选中月份变化的 monthCents）。 */
    val currentMonthCents: StateFlow<Long> = dateBoundary.flatMapLatest { dz ->
        dao.observeSumBetween(monthStart(YearMonth.from(dz.date), dz.zone), monthEnd(YearMonth.from(dz.date), dz.zone))
            .timed("records.currentMonthSum")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** 全库是否已有记录（空态引导，v08 3.3；初值 true 避免冷启动闪现引导）。 */
    val hasAnyRecords: StateFlow<Boolean> = dao.observeAnyRecord()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    // —— 软删除与撤销（v07 4.1/D3）——
    private val _lastDeletedId = MutableStateFlow<Long?>(null)
    val lastDeletedId: StateFlow<Long?> = _lastDeletedId.asStateFlow()

    fun delete(id: Long) = viewModelScope.launch {
        if (dao.softDelete(id, clock.millis()) > 0) _lastDeletedId.value = id
    }

    /** Snackbar 撤销按钮或超时后清除标记；超时清除后撤销不再生效。 */
    fun dismissUndo() {
        _lastDeletedId.value = null
    }

    fun undoDelete() = viewModelScope.launch {
        _lastDeletedId.value?.let { dao.restore(it) }
        _lastDeletedId.value = null
    }

    // —— 搜索（v07 7.1/7.2/D2）——
    private val _searchKeyword = MutableStateFlow("")
    private val _searchFilter = MutableStateFlow(SearchFilter())
    val searchFilter: StateFlow<SearchFilter> = _searchFilter.asStateFlow()

    /** 关键词变化（UI 输入直接透传，VM 内部 debounce 后才落到查询）。 */
    fun setSearchKeyword(raw: String) {
        _searchKeyword.value = raw
    }

    fun setSearchFilter(filter: SearchFilter) {
        _searchFilter.value = filter
    }

    /** 关键词是否还有效（防抖中不直接可见，UI 显示输入框原文即可）。 */
    val searchKeyword: StateFlow<String> = _searchKeyword.asStateFlow()

    /**
     * 搜索流：关键词 debounce 250ms + 筛选条件合并后 flatMapLatest。
     * 关键词为空且无筛选条件时返回 null（未激活搜索态）。
     */
    val searchResults: StateFlow<SearchResult?> = combine(
        _searchKeyword.debounce(SEARCH_DEBOUNCE_MS),
        _searchFilter,
    ) { kw, filter -> SearchQuery.build(kw) to filter }
        .flatMapLatest { (escaped, filter) ->
            if (escaped == null && !filter.isActive) {
                flowOf<SearchResult?>(null)
            } else {
                flow {
                    val records = dao.search(
                        SEARCH_FROM, SEARCH_TO,
                        filter.channel, filter.type, filter.category,
                        filter.minCents, filter.maxCents, escaped,
                    ).first()
                    val total = dao.countSearch(
                        SEARCH_FROM, SEARCH_TO,
                        filter.channel, filter.type, filter.category,
                        filter.minCents, filter.maxCents, escaped,
                    )
                    emit(SearchResult(records, total > TransactionDao.SEARCH_LIMIT))
                }.timed("search.results") { it?.records?.size ?: 0 }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // —— 统计页 ——
    private val _statsYear = MutableStateFlow(currentMonth().year)
    val statsYear: StateFlow<Int> = _statsYear.asStateFlow()

    /** 统计聚合的收支类型（v07 D7：收入开启后可切换）。 */
    private val _statsType = MutableStateFlow(TransactionRecord.TYPE_EXPENSE)
    val statsType: StateFlow<String> = _statsType.asStateFlow()

    fun setStatsType(type: String) {
        _statsType.value = type
    }

    val monthlySums: StateFlow<List<MonthSum>> = combine(_statsYear, dateBoundary) { y, dz -> y to dz }
        .flatMapLatest { (y, dz) ->
            dao.observeMonthlySums(yearStart(y, dz.zone), yearEnd(y, dz.zone))
                .timed("stats.monthlyTrend") { it.size }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 分类占比（v07 5.4/D7）：所选年份按 [statsType] 聚合。 */
    val categorySums: StateFlow<List<CategorySum>> = combine(_statsYear, _statsType, dateBoundary) { y, t, dz -> Triple(y, t, dz) }
        .flatMapLatest { (y, t, dz) ->
            dao.observeCategorySums(t, yearStart(y, dz.zone), yearEnd(y, dz.zone))
                .timed("stats.categorySums") { it.size }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 商户 Top 5（v07 5.4/D7）。 */
    val topMerchants: StateFlow<List<MerchantSum>> = combine(_statsYear, _statsType, dateBoundary) { y, t, dz -> Triple(y, t, dz) }
        .flatMapLatest { (y, t, dz) ->
            dao.observeTopMerchants(t, yearStart(y, dz.zone), yearEnd(y, dz.zone))
                .timed("stats.topMerchants") { it.size }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // —— 分类规则（v07 5.3/D5）——
    /** 规则页数据源与手动新增默认分类；ruleDao 为 null 时空列表。 */
    val rules: StateFlow<List<CategoryRule>> = (ruleDao?.observeAll() ?: flowOf(emptyList()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 新增规则：keyword 唯一，冲突时被 INSERT IGNORE 忽略。 */
    fun addRule(keyword: String, category: String, priority: Int) = viewModelScope.launch {
        val trimmed = keyword.trim()
        if (trimmed.isNotEmpty()) ruleDao?.insert(CategoryRule(keyword = trimmed, category = category, priority = priority))
    }

    fun updateRule(rule: CategoryRule) = viewModelScope.launch { ruleDao?.update(rule) }

    fun deleteRule(id: Long) = viewModelScope.launch { ruleDao?.delete(id) }

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

    /** 手动补记一笔：可指定日期时间与类型，渠道三选（微信/支付宝/手动）。 */
    fun addManual(
        amountCents: Long,
        channel: String,
        note: String,
        occurredAt: Long,
        counterparty: String = "",
        type: String = TransactionRecord.TYPE_EXPENSE,
        category: String? = null,
    ) = viewModelScope.launch {
        dao.insert(
            buildManualRecord(amountCents, channel, note, occurredAt, counterparty = counterparty, type = type, category = category),
        )
    }

    /** 编辑扩展（v07 3.1/D4）：金额、备注、时间、渠道、商户、类型、分类一起更新；source/dedupKey 不在更新列。 */
    fun updateRecord(
        id: Long,
        amountCents: Long,
        note: String,
        occurredAt: Long,
        channel: String,
        counterparty: String,
        type: String,
        category: String?,
    ) = viewModelScope.launch {
        dao.updateFull(id, amountCents, note, occurredAt, channel, counterparty, type, category)
    }

    internal fun groupByDay(records: List<TransactionRecord>, zone: ZoneId = this.zone): List<DayGroup> {
        val currentYear = clock.instant().atZone(zone).toLocalDate().year
        return records.groupBy { Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() }
            .map { (date, list) ->
                val fmt = if (date.year == currentYear) dayFmt else dayFmtWithYear
                DayGroup(date, date.format(fmt), list, list.sumOf { it.amountCents })
            }
            .sortedByDescending { it.date }
    }

    private fun currentMonth(): YearMonth = YearMonth.from(clock.instant().atZone(zone).toLocalDate())
    private fun monthStart(ym: YearMonth, zone: ZoneId = this.zone): Long = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    private fun monthEnd(ym: YearMonth, zone: ZoneId = this.zone): Long = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    private fun yearStart(y: Int, zone: ZoneId = this.zone): Long = LocalDate.of(y, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
    private fun yearEnd(y: Int, zone: ZoneId = this.zone): Long = LocalDate.of(y + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
    private fun startOfDay(date: LocalDate, zone: ZoneId): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    companion object {
        /** 搜索关键词防抖（v07 D2）。 */
        const val SEARCH_DEBOUNCE_MS = 250L

        /** 搜索时间范围：全历史（下界 0，上界 2100 年）。 */
        const val SEARCH_FROM = 0L
        const val SEARCH_TO = 4_102_444_800_000L
    }
}

/** 手动记录工厂：occurredAt 必须使用用户选择的记账时间，createdAt 才是入库时间；不参与自动判重（dedupKey 为 NULL）。 */
internal fun buildManualRecord(
    amountCents: Long,
    channel: String,
    note: String,
    occurredAt: Long,
    createdAt: Long = System.currentTimeMillis(),
    counterparty: String = "",
    type: String = TransactionRecord.TYPE_EXPENSE,
    category: String? = null,
): TransactionRecord = TransactionRecord(
    amountCents = amountCents,
    channel = channel,
    counterparty = counterparty,
    occurredAt = occurredAt,
    summary = "手动记账",
    note = note,
    createdAt = createdAt,
    source = TransactionRecord.SOURCE_MANUAL,
    type = type,
    category = category,
)
