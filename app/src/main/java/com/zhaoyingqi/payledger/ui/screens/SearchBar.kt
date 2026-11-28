package com.zhaoyingqi.payledger.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zhaoyingqi.payledger.data.TransactionDao
import com.zhaoyingqi.payledger.data.TransactionRecord
import com.zhaoyingqi.payledger.ui.SearchFilter
import com.zhaoyingqi.payledger.ui.SearchResult

// —— 明细页搜索区（v08 5.1 拆分自 RecordsScreen.kt）：搜索头、筛选条、结果列表与搜索入口 ——

@Composable
internal fun SearchHeader(
    keyword: String,
    filter: SearchFilter,
    onKeywordChange: (String) -> Unit,
    onFilterChange: (SearchFilter) -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "退出搜索")
            }
            OutlinedTextField(
                value = keyword,
                onValueChange = onKeywordChange,
                placeholder = { Text("搜商户 / 备注") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (keyword.isNotEmpty()) {
                        IconButton(onClick = { onKeywordChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "清空关键词")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChannelChip(
                label = "微信",
                selected = filter.channel == "WECHAT",
                onClick = { onFilterChange(filter.copy(channel = toggle(filter.channel, "WECHAT"))) },
            )
            ChannelChip(
                label = "支付宝",
                selected = filter.channel == "ALIPAY",
                onClick = { onFilterChange(filter.copy(channel = toggle(filter.channel, "ALIPAY"))) },
            )
            ChannelChip(
                label = "收入",
                selected = filter.type == TransactionRecord.TYPE_INCOME,
                onClick = {
                    onFilterChange(
                        filter.copy(
                            type = toggle(filter.type, TransactionRecord.TYPE_INCOME)?.takeIf { it == TransactionRecord.TYPE_INCOME },
                        ),
                    )
                },
            )
            ChannelChip(
                label = "支出",
                selected = filter.type == TransactionRecord.TYPE_EXPENSE,
                onClick = {
                    onFilterChange(
                        filter.copy(
                            type = toggle(filter.type, TransactionRecord.TYPE_EXPENSE)?.takeIf { it == TransactionRecord.TYPE_EXPENSE },
                        ),
                    )
                },
            )
        }
    }
}

private fun toggle(current: String?, value: String): String? = if (current == value) null else value

/** 单个筛选条件条（渠道/类型快捷筛选）。 */
@Composable
internal fun ChannelChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
internal fun ColumnScope.SearchResultsArea(
    active: Boolean,
    results: SearchResult?,
    onEdit: (TransactionRecord) -> Unit,
    onAdd: () -> Unit,
) {
    when {
        !active -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                "输入关键词或选择筛选条件",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        results != null && results.records.isEmpty() -> Box(
            Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("没有符合条件的记录", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onAdd) { Text("手动记一笔") }
            }
        }
        results != null -> Column(Modifier.weight(1f).fillMaxWidth()) {
            Text(
                "找到 ${results.records.size} 条结果",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            if (results.truncated) {
                Text(
                    "结果超过 ${TransactionDao.SEARCH_LIMIT} 条，仅显示最新的一部分，请缩小范围",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(results.records, key = { it.id }) { record ->
                    RecordCard(
                        record = record,
                        onClick = { onEdit(record) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}
/** 搜索入口行（v07 7.3）：点击进入搜索态。 */
@Composable
internal fun SearchEntryRow(onOpenSearch: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onOpenSearch),
        shape = RoundedCornerShape(14.dp),
        color = scheme.surfaceContainerLow,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "搜索商户、备注",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

// —— 预览（v08 5.2，亮/暗）——

@Preview(name = "搜索入口（亮）", showBackground = true)
@Composable
private fun SearchEntryLightPreview() {
    MaterialTheme { SearchEntryRow(onOpenSearch = {}) }
}

@Preview(name = "搜索入口（暗）", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SearchEntryDarkPreview() {
    MaterialTheme { SearchEntryRow(onOpenSearch = {}) }
}

@Preview(name = "搜索头与筛选（亮）", showBackground = true)
@Composable
private fun SearchHeaderLightPreview() {
    MaterialTheme {
        SearchHeader(
            keyword = "星巴克",
            filter = SearchFilter(channel = "WECHAT"),
            onKeywordChange = {},
            onFilterChange = {},
            onClose = {},
        )
    }
}

@Preview(name = "搜索头与筛选（暗）", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SearchHeaderDarkPreview() {
    MaterialTheme {
        SearchHeader(
            keyword = "",
            filter = SearchFilter(),
            onKeywordChange = {},
            onFilterChange = {},
            onClose = {},
        )
    }
}
