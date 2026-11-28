package com.zhaoyingqi.payledger.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zhaoyingqi.payledger.PayLedgerApplication
import com.zhaoyingqi.payledger.budget.BudgetAlertNotifier
import com.zhaoyingqi.payledger.budget.BudgetType
import com.zhaoyingqi.payledger.data.CsvExporter
import com.zhaoyingqi.payledger.data.ExportResult
import com.zhaoyingqi.payledger.ui.VENDOR_GUIDE_STEPS
import com.zhaoyingqi.payledger.ui.vendorGuideKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 设置指南：任何状态都可达（顶栏「帮助」），不受授权状态影响。 */
@Composable
fun HelpScreen(onBack: () -> Unit, onOpenRules: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as PayLedgerApplication
    val incomeEnabled by app.incomeEnabled.collectAsStateWithLifecycle()
    val budgetSettings by app.budgetSettings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var exportStatus by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri == null) {
            // 用户取消：不产生文件，也不提示（v07 8.3）
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            when (val r = CsvExporter(context, app.dao).export(uri, CsvExporter.EXPORT_FROM, CsvExporter.EXPORT_TO)) {
                is ExportResult.Success -> exportStatus = "导出成功"
                is ExportResult.Failure -> exportStatus = "导出失败：${r.message}"
                ExportResult.Cancelled -> {}
            }
        }
    }
    var showBudgetDialog by remember { mutableStateOf(false) }

    // 预算提醒权限（v08 D5）：只在从关闭改为启用时请求，拒绝显示一次性说明
    var budgetPermissionHint by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) budgetPermissionHint = true
    }
    var accEnabled by remember { mutableStateOf(checkAccessibility(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) accEnabled = checkAccessibility(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
    ) {
        item {
            SecondaryHeader(
                title = "设置指南",
                subtitle = "完成设置后，付款即自动记账",
                onBack = onBack,
            )
        }
        item {
            GuideStep(
                number = 1,
                title = "开启系统「通知使用权」",
                body = "在系统列表中找到 PayLedger 并打开开关。刚升级或重装过应用时，" +
                    "请先把开关关闭再重新打开一次；打开 App 时也会自动尝试重连。",
                footer = {
                    Button(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text("去开启通知使用权")
                    }
                },
            )
        }
        item {
            GuideStep(
                number = 2,
                title = "微信打开「通知显示消息详情」",
                body = "微信 → 我 → 设置 → 新消息通知 → 打开「通知显示消息详情」。\n" +
                    "此开关关闭时，付款通知里没有金额，任何记账应用都无法自动记账。",
                highlight = true,
            )
        }
        item {
            GuideStep(
                number = 3,
                title = "允许微信显示系统通知",
                body = "系统设置 → 应用 → 微信 → 通知管理：打开「允许通知」，并勾选横幅 / 锁屏显示。",
            )
        }
        item {
            val vendorSteps = remember { VENDOR_GUIDE_STEPS.getValue(vendorGuideKey(Build.MANUFACTURER)) }
            val brand = remember { Build.MANUFACTURER.replaceFirstChar { it.uppercase() } }
            GuideStep(
                number = 4,
                title = "允许 PayLedger 后台运行",
                body = "系统设置中允许 PayLedger「自启动」并关闭电池优化（部分国产 ROM 需要）。\n" +
                    "开机、应用升级后会自动请求重绑通知监听。\n" +
                    "本机品牌（$brand）建议按以下路径确认：",
                footer = {
                    Column {
                        vendorSteps.forEachIndexed { index, step ->
                            Text(
                                "${index + 1}. $step",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (index < vendorSteps.lastIndex) Spacer(Modifier.height(4.dp))
                        }
                    }
                },
            )
        }
        item {
            GuideStep(
                number = 5,
                title = "开启「无障碍」兜底（推荐）",
                body = "付款时若微信/支付宝正好在前台，系统可能不发通知栏通知，通知监听就会漏记。" +
                    "开启 PayLedger 的无障碍服务后，可从支付成功页面读取金额补记。" +
                    "它只监听微信/支付宝两个应用、耗电极小、不上传数据，也能帮通知监听在后台恢复连接。",
                footer = {
                    Column {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (accEnabled) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            },
                        ) {
                            Text(
                                if (accEnabled) "当前状态：已开启 ✓" else "当前状态：未开启",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (accEnabled) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer
                                },
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text(if (accEnabled) "管理无障碍" else "去开启无障碍")
                        }
                    }
                },
            )
        }
        item {
            GuideStep(
                number = 6,
                title = "验证自动记账",
                body = "付款一笔小额：通知栏能看到金额字样即设置成功，打开 PayLedger 会自动出现记录。\n" +
                    "也可先在调试页点「模拟微信付款」验证记账链路（仅调试版）。",
            )
        }
        item {
            Surface(
                Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("功能速览", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "搜索：明细页顶部入口，按商户/备注关键词与渠道、类型筛选。\n" +
                            "撤销：删除记录后 5 秒内可撤销，7 天后自动彻底清理。\n" +
                            "分类：入账时按「分类规则」自动归类，之后可手动修改。\n" +
                            "收入：开启「记收入」后，收款/到账类通知会计入本月收入。\n" +
                            "预算：每日类型按「日额 × 当月天数」折算月总额，剩余每天可用 = " +
                            "(月总额 − 本月已用) ÷ 剩余天数（含今天）；每月类型按月支出 80% / 100% " +
                            "各提醒一次；明细页顶部显示进度。\n" +
                            "导出：全部记录导出为 CSV，可用 Excel/WPS 打开。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            ManagementSection(
                incomeEnabled = incomeEnabled,
                onToggleIncome = { app.setIncomeEnabled(it) },
                onOpenRules = onOpenRules,
                onOpenBudget = { showBudgetDialog = true },
                budgetSummary = budgetSummaryText(budgetSettings.type),
                onExport = { exportLauncher.launch(CsvExporter.defaultFileName(System.currentTimeMillis())) },
                exportStatus = exportStatus,
                budgetPermissionHint = budgetPermissionHint,
            )
        }
    }

    LaunchedEffect(exportStatus) {
        if (exportStatus != null) {
            delay(4_000)
            exportStatus = null
        }
    }

    if (showBudgetDialog) {
        BudgetSettingsDialog(
            current = budgetSettings,
            onDismiss = { showBudgetDialog = false },
            onSave = { settings ->
                showBudgetDialog = false
                budgetPermissionHint = false
                val enabling = budgetSettings.type == BudgetType.OFF && settings.type != BudgetType.OFF
                app.setBudgetSettings(settings)
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

/** 预算行的摘要文案（设置行副标题）。 */
private fun budgetSummaryText(type: BudgetType): String = when (type) {
    BudgetType.DAILY -> "已开启 · 每日额度"
    BudgetType.MONTHLY -> "已开启 · 每月预算"
    BudgetType.OFF -> "未开启"
}

/** 数据与设置区（v07/v08）：记收入开关、分类规则入口、预算提醒、CSV 导出。 */
@Composable
private fun ManagementSection(
    incomeEnabled: Boolean,
    onToggleIncome: (Boolean) -> Unit,
    onOpenRules: () -> Unit,
    onOpenBudget: () -> Unit,
    budgetSummary: String,
    onExport: () -> Unit,
    exportStatus: String?,
    budgetPermissionHint: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = scheme.surfaceContainerLow,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("记收入", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "识别收款/到账类通知并入账为收入；关闭时这类通知照旧忽略",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(checked = incomeEnabled, onCheckedChange = onToggleIncome)
            }
            ManagementRow(
                title = "分类规则",
                subtitle = "维护商户关键词与分类的对应关系",
                onClick = onOpenRules,
            )
            ManagementRow(
                title = "预算提醒",
                subtitle = budgetSummary,
                onClick = onOpenBudget,
            )
            if (budgetPermissionHint) {
                Text(
                    "未获通知权限，将不会收到提醒；预算进度仍正常显示",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            ManagementRow(
                title = "导出 CSV",
                subtitle = "全部记录导出为表格文件，可用 Excel/WPS 打开",
                onClick = onExport,
            )
            if (exportStatus != null) {
                Text(
                    exportStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (exportStatus.startsWith("导出成功")) scheme.primary else scheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** 设置区单行入口：标题 + 说明 + 右箭头。 */
@Composable
private fun ManagementRow(title: String, subtitle: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
        )
    }
}

/** 检测本应用的无障碍服务是否已开启（帮助页经 ON_RESUME 刷新，调试页随「刷新」重读）。 */
internal fun checkAccessibility(context: android.content.Context): Boolean {
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
    return enabled?.contains(context.packageName) == true
}
