package com.zhaoyingqi.payledger

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.room.withTransaction
import com.zhaoyingqi.payledger.budget.BudgetAlertNotifier
import com.zhaoyingqi.payledger.budget.BudgetAlertPolicy
import com.zhaoyingqi.payledger.budget.BudgetPlan
import com.zhaoyingqi.payledger.budget.BudgetSettings
import com.zhaoyingqi.payledger.budget.BudgetType
import com.zhaoyingqi.payledger.data.CategoryMatcher
import com.zhaoyingqi.payledger.data.LedgerDatabase
import com.zhaoyingqi.payledger.data.TransactionDao
import com.zhaoyingqi.payledger.data.TransactionRecord
import com.zhaoyingqi.payledger.notification.DedupKey
import com.zhaoyingqi.payledger.notification.DedupWindow
import com.zhaoyingqi.payledger.notification.DuplicatePolicy
import com.zhaoyingqi.payledger.notification.ListenerHealth
import com.zhaoyingqi.payledger.notification.NotificationRingBuffer
import com.zhaoyingqi.payledger.notification.ParsedPayment
import com.zhaoyingqi.payledger.ui.DateZone
import com.zhaoyingqi.payledger.ui.dateBoundaryFlow
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

class PayLedgerApplication : Application() {
    val database: LedgerDatabase by lazy { LedgerDatabase.build(this) }
    val dao: TransactionDao get() = database.transactionDao()
    val recentNotifications = NotificationRingBuffer()

    /** 与进程同生命周期：服务被回收时不会取消正在进行的入库，避免隐形漏记。 */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 预算判定与展示共用的时间源。 */
    val clock: Clock = Clock.systemDefaultZone()

    private val settingsPrefs: SharedPreferences by lazy {
        getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE)
    }

    /** 记收入开关（v07 6.1/D6）：解析入口读 StateFlow 缓存，避免每条通知读 SharedPreferences。 */
    private val _incomeEnabled = MutableStateFlow(false)
    val incomeEnabled: StateFlow<Boolean> = _incomeEnabled.asStateFlow()

    fun setIncomeEnabled(enabled: Boolean) {
        _incomeEnabled.value = enabled
        settingsPrefs.edit().putBoolean(KEY_INCOME_ENABLED, enabled).apply()
    }

    /** 预算设置（v08 D3）：内存缓存供提醒判定与界面共享。 */
    private val _budgetSettings = MutableStateFlow(BudgetSettings())
    val budgetSettings: StateFlow<BudgetSettings> = _budgetSettings.asStateFlow()

    fun setBudgetSettings(settings: BudgetSettings) {
        _budgetSettings.value = settings
        BudgetSettings.writeTo(settingsPrefs, settings)
        if (settings.type != BudgetType.OFF) BudgetAlertNotifier.ensureChannel(this)
    }

    override fun onCreate() {
        super.onCreate()
        _incomeEnabled.value = settingsPrefs.getBoolean(KEY_INCOME_ENABLED, false)
        _budgetSettings.value = BudgetSettings.load(settingsPrefs)
        scrubLegacySummariesOnce()
        purgeExpiredDeleted()
        startBudgetMonitoring()
    }

    /** 旧版本把通知原文写进了 summary，升级后一次性清理为结构化摘要。 */
    private fun scrubLegacySummariesOnce() {
        applicationScope.launch {
            val prefs = getSharedPreferences(ListenerHealth.PREFS_NAME, MODE_PRIVATE)
            if (prefs.getBoolean(KEY_SUMMARY_SCRUBBED, false)) return@launch
            runCatching { dao.scrubLegacySummaries() }
                .onSuccess { prefs.edit().putBoolean(KEY_SUMMARY_SCRUBBED, true).apply() }
                .onFailure { Log.w(TAG, "scrub legacy summaries failed", it) }
        }
    }

    /** 过期清理（v07 4.3/D3）：进程启动时一次性物理删除软删除超过 7 天的记录，非周期任务。 */
    private fun purgeExpiredDeleted() {
        applicationScope.launch {
            runCatching { dao.purgeDeleted(System.currentTimeMillis() - DELETED_RETENTION_MS) }
                .onFailure { Log.w(TAG, "purge deleted records failed", it) }
        }
    }

    /**
     * 预算提醒订阅（v08 4.3/D4）：Application 级订阅，界面不在前台也能判定；
     * 本月与今日两个支出流随 dateBoundaryFlow 切月/切日，关闭时不再订阅支出查询。
     * 发通知与写状态顺序执行，状态写入用 commit()（同步），避免重启后同档重复。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startBudgetMonitoring() {
        applicationScope.launch {
            combine(dateBoundaryFlow(clock, this@PayLedgerApplication), _budgetSettings) { dz, s ->
                dz to BudgetPlan.current(s)
            }.flatMapLatest { (dz, plan) ->
                if (plan == BudgetPlan.NONE) {
                    flowOf(BudgetEvaluation(dz, plan, 0L, 0L))
                } else {
                    combine(
                        dao.observeSumBetween(monthStartMillis(dz), monthEndMillis(dz)),
                        dao.observeSumBetween(startOfDayMillis(dz.date, dz.zone), startOfDayMillis(dz.date.plusDays(1), dz.zone)),
                    ) { monthSum, todaySum -> BudgetEvaluation(dz, plan, monthSum, todaySum) }
                }
            }.collect { evaluateBudgetAlerts(it) }
        }
    }

    private suspend fun evaluateBudgetAlerts(e: BudgetEvaluation) {
        val month = YearMonth.from(e.boundary.date)
        when (val plan = e.plan) {
            is BudgetPlan.Monthly -> {
                val mask = BudgetSettings.monthlyAlertedMask(settingsPrefs, month)
                val decision = BudgetAlertPolicy.evaluateMonth(e.monthSumCents, plan.cents, mask)
                if (decision.shouldNotify) {
                    BudgetAlertNotifier.notifyMonth(this, decision.level, e.monthSumCents, plan.cents)
                    BudgetSettings.writeMonthlyAlertedMask(settingsPrefs, month, decision.newMask)
                }
            }
            is BudgetPlan.Daily -> {
                val dateIso = e.boundary.date.toString()
                if (BudgetAlertPolicy.evaluateDay(e.todaySumCents, plan.cents, BudgetSettings.dailyAlertedDate(settingsPrefs), dateIso)) {
                    BudgetAlertNotifier.notifyDaily(this, e.todaySumCents, plan.cents)
                    BudgetSettings.writeDailyAlertedDate(settingsPrefs, dateIso)
                }
            }
            BudgetPlan.NONE -> Unit
        }
    }

    private fun monthStartMillis(dz: DateZone): Long =
        YearMonth.from(dz.date).atDay(1).atStartOfDay(dz.zone).toInstant().toEpochMilli()

    private fun monthEndMillis(dz: DateZone): Long =
        YearMonth.from(dz.date).plusMonths(1).atDay(1).atStartOfDay(dz.zone).toInstant().toEpochMilli()

    private fun startOfDayMillis(date: LocalDate, zone: java.time.ZoneId): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    /** 一次预算判定所需的输入：日期边界 + 当前计划 + 本月/今日支出。 */
    private data class BudgetEvaluation(
        val boundary: DateZone,
        val plan: BudgetPlan,
        val monthSumCents: Long,
        val todaySumCents: Long,
    )

    /**
     * 入账（v06 判重 + v07 分类/收入）：
     * - 判重窗口按采集时间 [capturedAt]（D1），候选整行交 [DuplicatePolicy] 判定（D2）；
     * - [dedupKey] 唯一索引 + INSERT IGNORE 兜底极端并发（D3）；
     * - 交易时间用文案解析时间（[ParsedPayment.occurredAt]），无则回退到达时间并标记估算（O-07）；
     * - 类型来自 [ParsedPayment.type]（v07 D6），入账时按规则归类一次（v07 D5，不追溯）。
     * 事务内查重+插入保证双通道并发不重复。通知监听与模拟注入共用。
     */
    suspend fun savePayment(
        parsed: ParsedPayment,
        occurredAt: Long,
        source: String = TransactionRecord.SOURCE_NOTIFICATION,
    ): Boolean {
        val transactionAt = parsed.occurredAt ?: occurredAt
        val category = CategoryMatcher.match(parsed.counterparty, database.categoryRuleDao().getAll())
        val incoming = TransactionRecord(
            amountCents = parsed.amountCents,
            channel = parsed.channel.name,
            counterparty = parsed.counterparty,
            occurredAt = transactionAt,
            summary = parsed.summary,
            createdAt = System.currentTimeMillis(),
            type = parsed.type,
            category = category,
            source = source,
            dedupKey = DedupKey.of(parsed.channel.name, parsed.amountCents, parsed.counterparty, transactionAt),
            timeEstimated = parsed.timeEstimated,
            capturedAt = occurredAt,
        )
        val window = DedupWindow.of(occurredAt)
        return database.withTransaction {
            val candidates = dao.findDuplicateCandidates(
                parsed.channel.name, parsed.amountCents, window.first, window.last,
            )
            if (candidates.any { DuplicatePolicy.isDuplicate(it, incoming) }) {
                return@withTransaction false
            }
            dao.insert(incoming) != -1L
        }
    }

    companion object {
        private const val TAG = "PayLedgerApp"
        private const val KEY_SUMMARY_SCRUBBED = "summary_scrubbed_v1"
        const val PREFS_SETTINGS = "settings"
        const val KEY_INCOME_ENABLED = "income_enabled"

        /** 软删除记录保留期（v07 D3）：超过后启动清理时物理删除。 */
        const val DELETED_RETENTION_MS = 7L * 24 * 60 * 60 * 1000
    }
}
