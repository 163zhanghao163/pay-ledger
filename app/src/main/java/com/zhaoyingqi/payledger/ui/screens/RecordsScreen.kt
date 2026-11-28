package com.zhaoyingqi.payledger.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zhaoyingqi.payledger.PayLedgerApplication
import com.zhaoyingqi.payledger.budget.BudgetType
import com.zhaoyingqi.payledger.data.TransactionRecord
import com.zhaoyingqi.payledger.notification.ListenerStatus
import com.zhaoyingqi.payledger.ui.PayLedgerViewModel
import com.zhaoyingqi.payledger.ui.SearchFilter
import com.zhaoyingqi.payledger.ui.formatCents
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 明细页：今日/当月支出卡 + 预算进度 + 月份选择 + 按日分组记录列表 + 搜索 + FAB 手动记账。 */
@Composable
fun RecordsScreen(vm: PayLedgerViewModel) {
    val groups by vm.groups.collectAsStateWithLifecycle()
    val todayCents by vm.todayCents.collectAsStateWithLifecycle()
    val monthCents by vm.monthCents.collectAsStateWithLifecycle()
    val monthIncomeCents by vm.monthIncomeCents.collectAsStateWithLifecycle()
    val selectedMonth by vm.selectedMonth.collectAsStateWithLifecycle()
    val atLatestMonth by vm.atLatestMonth.collectAsStateWithLifecycle()
    val rules by vm.rules.collectAsStateWithLifecycle()
    val searchKeyword by vm.searchKeyword.collectAsStateWithLifecycle()
    val searchFilter by vm.searchFilter.collectAsStateWithLifecycle()
    val searchResults by vm.searchResults.collectAsStateWithLifecycle()
    val budgetToday by vm.today.collectAsStateWithLifecycle()
    val currentMonthSpent by vm.currentMonthCents.collectAsStateWithLifecycle()
    val hasAnyRecords by vm.hasAnyRecords.collectAsStateWithLifecycle()
    val app = LocalContext.current.applicationContext as PayLedgerApplication
    val incomeEnabled by app.incomeEnabled.collectAsStateWithLifecycle()
    val budgetSettings by app.budgetSettings.collectAsStateWithLifecycle()
    val listenerStatus by ListenerStatus.snapshot.collectAsStateWithLifecycle()
    val recordCount = remember(groups) { groups.sumOf { it.records.size } }
    var editing by remember { mutableStateOf<TransactionRecord?>(null) }
    var deleting by remember { mutableStateOf<TransactionRecord?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var searchMode by remember { mutableStateOf(false) }
    var showBudgetDialog by remember { mutableStateOf(false) }
    val searchActive = searchKeyword.isNotBlank() || searchFilter.isActive
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // —— 软删除撤销（v07 4.2/D3）：Snackbar 显示 5 秒，点「撤销」恢复，超时后不可撤销 ——
    val snackbarHostState = remember { SnackbarHostState() }
    // 预算设置（明细页入口）：首次启用预算时请求通知权限，拒绝则用 Snackbar 提示影响
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            scope.launch {
                snackbarHostState.showSnackbar("未授予通知权限：达到预算阈值时不会提醒，可到系统设置开启")
            }
        }
    }
    val lastDeletedId by vm.lastDeletedId.collectAsStateWithLifecycle()
    LaunchedEffect(lastDeletedId) {
        if (lastDeletedId == null) return@LaunchedEffect
        val snackbar = launch {
            val result = snackbarHostState.showSnackbar(
                message = "已删除该记录",
                actionLabel = "撤销",
                duration = SnackbarDuration.Indefinite,
            )
            if (result == SnackbarResult.ActionPerformed) vm.undoDelete()
        }
        delay(5_000)
        vm.dismissUndo() // lastDeletedId 置空 → 本 LaunchedEffect 重启并取消 Snackbar
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (searchMode) {
                SearchHeader(
                    keyword = searchKeyword,
                    filter = searchFilter,
                    onKeywordChange = vm::setSearchKeyword,
                    onFilterChange = vm::setSearchFilter,
                    onClose = {
                        searchMode = false
                        vm.setSearchKeyword("")
                        vm.setSearchFilter(SearchFilter())
                    },
                )
                SearchResultsArea(
                    active = searchActive,
                    results = searchResults,
                    onEdit = { editing = it },
                    onAdd = { showAdd = true },
                )
            } else {
                SummaryCard(
                    todayCents = todayCents,
                    monthCents = monthCents,
                    monthIncomeCents = monthIncomeCents,
                    month = selectedMonth,
                    recordCount = recordCount,
                    atLatest = atLatestMonth,
                    onPrev = vm::prevMonth,
                    onNext = vm::nextMonth,
                    onOpenPicker = { showPicker = true },
                )
                SearchEntryRow(onOpenSearch = { searchMode = true })
                // 预算进度/设置入口（v08 4.5）：跨零点由 dateBoundaryFlow 重算
                BudgetProgress(
                    settings = budgetSettings,
                    monthSpentCents = currentMonthSpent,
                    today = budgetToday,
                    onEdit = { showBudgetDialog = true },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                if (groups.isEmpty()) {
                    if (!hasAnyRecords && listenerStatus.authorized) {
                        // 空库引导（v08 3.3）：无记录且已授权时给出三路状态检查清单
                        OnboardingEmptyState(
                            authorized = listenerStatus.authorized,
                            notificationConnected = listenerStatus.notificationConnected,
                            accessibilityConnected = listenerStatus.accessibilityConnected,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            onAdd = { showAdd = true },
                        )
                    } else {
                        EmptyState(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            onAdd = { showAdd = true },
                        )
                    }
                } else {
                    LazyColumn(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 104.dp),
                    ) {
                        groups.forEach { group ->
                            item(key = "header-${group.date.toEpochDay()}") {
                                DayHeader(group.label, group.records.size, group.totalCents)
                            }
                            items(group.records, key = { it.id }) { record ->
                                RecordCard(
                                    record = record,
                                    onClick = { editing = record },
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        if (!searchMode && groups.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("记一笔") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
                shape = RoundedCornerShape(18.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        }
        SnackbarHost(
            snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 84.dp),
        )
    }

    if (showPicker) {
        MonthPickerDialog(
            current = selectedMonth,
            latest = vm.latestMonth,
            onPick = {
                vm.pickMonth(it)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
    if (showAdd) {
        ManualAddDialog(
            incomeEnabled = incomeEnabled,
            rules = rules,
            onDismiss = { showAdd = false },
            onSave = { cents, channel, note, occurredAt, counterparty, type, category ->
                vm.addManual(cents, channel, note, occurredAt, counterparty = counterparty, type = type, category = category)
                vm.pickMonth(monthOf(occurredAt))
                showAdd = false
            },
        )
    }
    editing?.let { record ->
        EditRecordSheet(
            record = record,
            onDismiss = { editing = null },
            onSave = { id, cents, note, occurredAt, channel, counterparty, type, category ->
                vm.updateRecord(id, cents, note, occurredAt, channel, counterparty, type, category)
                vm.pickMonth(monthOf(occurredAt))
                editing = null
            },
            onDelete = {
                deleting = record
                editing = null
            },
        )
    }
    deleting?.let { record ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除记录") },
            text = { Text("删除 ${formatCents(record.amountCents)} 这条记录？\n删除后 5 秒内可撤销，7 天后自动清理。") },
            confirmButton = {
                TextButton(onClick = { vm.delete(record.id); deleting = null }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
    if (showBudgetDialog) {
        BudgetSettingsDialog(
            current = budgetSettings,
            onDismiss = { showBudgetDialog = false },
            onSave = { settings ->
                showBudgetDialog = false
                val enabling = budgetSettings.type == BudgetType.OFF && settings.type != BudgetType.OFF
                app.setBudgetSettings(settings)
                // 从关闭改为启用时才请求通知权限（v08 D5），与帮助页入口行为一致
                if (enabling && Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
        )
    }
}

// —— 月度汇总与列表 ——

@Composable
private fun SummaryCard(
    todayCents: Long,
    monthCents: Long,
    monthIncomeCents: Long,
    month: YearMonth,
    recordCount: Int,
    atLatest: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpenPicker: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.extraLarge
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        shape = shape,
        color = scheme.primary,
        contentColor = scheme.onPrimary,
        shadowElevation = 6.dp,
    ) {
        Box(Modifier.clip(shape)) {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(
                    color = scheme.onPrimary.copy(alpha = 0.09f),
                    radius = size.minDimension * 0.48f,
                    center = Offset(size.width * 0.94f, 0f),
                )
                drawCircle(
                    color = scheme.onPrimary.copy(alpha = 0.06f),
                    radius = size.minDimension * 0.32f,
                    center = Offset(size.width * 0.05f, size.height * 1.02f),
                )
            }
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (recordCount > 0) {
                                "${month.monthValue}月支出 · ${recordCount}笔"
                            } else {
                                "${month.monthValue}月支出"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.onPrimary.copy(alpha = 0.78f),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            formatCents(monthCents),
                            style = MaterialTheme.typography.displaySmall,
                            color = scheme.onPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (monthIncomeCents > 0) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "本月收入 +${formatCents(monthIncomeCents)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = scheme.onPrimary.copy(alpha = 0.85f),
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = scheme.onPrimary.copy(alpha = 0.15f),
                    ) {
                        Column(
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.End,
                        ) {
                            Text(
                                "今日",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onPrimary.copy(alpha = 0.75f),
                            )
                            Text(
                                formatCents(todayCents),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MonthArrowButton(
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        description = "上个月",
                        enabled = true,
                        onClick = onPrev,
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        color = scheme.onPrimary.copy(alpha = 0.14f),
                    ) {
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable(onClick = onOpenPicker)
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${month.year}年${month.monthValue}月",
                                style = MaterialTheme.typography.titleSmall,
                                color = scheme.onPrimary,
                            )
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = "选择月份",
                                tint = scheme.onPrimary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    MonthArrowButton(
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        description = "下个月",
                        enabled = !atLatest,
                        onClick = onNext,
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthArrowButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = if (enabled) 0.14f else 0.07f),
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(42.dp)) {
            Icon(
                icon,
                contentDescription = description,
                tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = if (enabled) 0.95f else 0.35f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** 记录所属月份（用于保存后自动切换到对应月份）。 */
private fun monthOf(epochMillis: Long): YearMonth =
    YearMonth.from(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
