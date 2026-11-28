package com.zhaoyingqi.payledger.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zhaoyingqi.payledger.data.CategoryRule
import com.zhaoyingqi.payledger.ui.PayLedgerViewModel

/**
 * 分类规则管理页（v07 5.3/D5）：列表、新增、编辑、删除、启停。
 * 规则只在入账时匹配一次，修改不追溯已有记录。
 */
@Composable
internal fun CategoryRulesScreen(vm: PayLedgerViewModel, onBack: () -> Unit) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<CategoryRule?>(null) }
    var adding by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SecondaryHeader(
                    title = "分类规则",
                    subtitle = "付款入账时按商户名匹配关键词归类；修改只影响之后的记录",
                    onBack = onBack,
                )
            }
            items(rules, key = { it.id }) { rule ->
                RuleRow(
                    rule = rule,
                    onClick = { editing = rule },
                    onToggle = { vm.updateRule(rule.copy(enabled = it)) },
                )
            }
            if (rules.isEmpty()) {
                item {
                    Surface(
                        Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Text(
                            "还没有分类规则。\n新增「关键词 → 分类」后，之后入账的记录会自动归类。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { adding = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("新增规则") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        )
    }

    if (adding) {
        RuleEditDialog(
            title = "新增规则",
            initialKeyword = "",
            initialCategory = null,
            initialPriority = 10,
            onConfirm = { keyword, category, priority ->
                vm.addRule(keyword, category, priority)
                adding = false
            },
            onDismiss = { adding = false },
        )
    }
    editing?.let { rule ->
        RuleEditDialog(
            title = "编辑规则",
            initialKeyword = rule.keyword,
            initialCategory = rule.category,
            initialPriority = rule.priority,
            onDelete = {
                vm.deleteRule(rule.id)
                editing = null
            },
            onConfirm = { keyword, category, priority ->
                vm.updateRule(rule.copy(keyword = keyword.trim(), category = category, priority = priority))
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun RuleRow(rule: CategoryRule, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = scheme.surfaceContainerLow,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        rule.keyword,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.width(8.dp))
                    if (rule.priority >= 20) {
                        Surface(shape = RoundedCornerShape(8.dp), color = scheme.primaryContainer) {
                            Text(
                                "具体词",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    "归入「${rule.category}」",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            Switch(checked = rule.enabled, onCheckedChange = onToggle)
        }
    }
}

/** 新增/编辑规则弹窗：关键词 + 分类下拉 + 具体词优先开关。 */
@Composable
private fun RuleEditDialog(
    title: String,
    initialKeyword: String,
    initialCategory: String?,
    initialPriority: Int,
    onDelete: (() -> Unit)? = null,
    onConfirm: (keyword: String, category: String, priority: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var keyword by remember { mutableStateOf(initialKeyword) }
    var category by remember { mutableStateOf(initialCategory ?: CategoryRule.CATEGORY_LABELS.first()) }
    var specific by remember { mutableStateOf(initialPriority >= 20) }
    var categoryMenu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { if (it.length <= 30) keyword = it },
                    label = { Text("关键词") },
                    placeholder = { Text("商户名包含即命中，如「星巴克」") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "分类",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(5.dp))
                Box {
                    Button(
                        onClick = { categoryMenu = true },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(13.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(category, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "选择分类", modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                        CategoryRule.CATEGORY_LABELS.forEach { label ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    category = label
                                    categoryMenu = false
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("具体词优先", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "宽泛词（如「美团」）之前先匹配更具体的词（如「美团打车」）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = specific, onCheckedChange = { specific = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(keyword, category, if (specific) 20 else 10) },
                enabled = keyword.trim().isNotEmpty(),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            ) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Text("删除") }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}
