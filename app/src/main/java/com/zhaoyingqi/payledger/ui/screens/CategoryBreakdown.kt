package com.zhaoyingqi.payledger.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhaoyingqi.payledger.data.CategorySum
import com.zhaoyingqi.payledger.data.MerchantSum
import com.zhaoyingqi.payledger.ui.formatCents
import kotlin.math.roundToInt

/** 分类占比卡（v08 2.1 拆分自 StatsScreen）：横向条形按金额降序，每行合并读屏语义。 */
@Composable
internal fun CategoryBreakdownCard(sums: List<CategorySum>) {
    val total = remember(sums) { sums.sumOf { it.totalCents } }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("分类占比", style = MaterialTheme.typography.titleMedium)
            Text(
                "入账时按分类规则归类，修改规则不追溯已有记录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (sums.isEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "本年还没有已归类的记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    sums.forEach { sum ->
                        val fraction = if (total > 0) sum.totalCents.toFloat() / total else 0f
                        val percent = (fraction * 100).roundToInt()
                        Column(
                            // v08 2.3（D6）：整行合并为一个读屏节点
                            Modifier.fillMaxWidth().clearAndSetSemantics {
                                contentDescription = "${sum.category}，${formatCents(sum.totalCents)}，占 $percent%"
                            },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(sum.category, style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "${formatCents(sum.totalCents)} · $percent%",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Box(
                                Modifier.fillMaxWidth().height(6.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            ) {
                                Box(
                                    Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight()
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 商户 Top 5 卡（v08 2.1 拆分自 StatsScreen）：每行合并读屏语义。 */
@Composable
internal fun TopMerchantsCard(sums: List<MerchantSum>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("商户 Top 5", style = MaterialTheme.typography.titleMedium)
            Text(
                "消费最多的 5 个商户",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (sums.isEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "本年还没有带商户名的记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sums.forEachIndexed { index, sum ->
                        Row(
                            Modifier.fillMaxWidth().clearAndSetSemantics {
                                contentDescription = "第${index + 1}名，${sum.counterparty}，${formatCents(sum.totalCents)}"
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(22.dp),
                            )
                            Text(
                                sum.counterparty,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                formatCents(sum.totalCents),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun previewCategories() = listOf(
    CategorySum("餐饮", 345_60L),
    CategorySum("交通", 220_00L),
    CategorySum("购物", 150_00L),
)

@Preview(name = "分类占比（亮）", showBackground = true)
@Composable
private fun CategoryBreakdownLightPreview() {
    MaterialTheme {
        CategoryBreakdownCard(previewCategories())
    }
}

@Preview(name = "分类占比（暗）", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CategoryBreakdownDarkPreview() {
    MaterialTheme {
        CategoryBreakdownCard(previewCategories())
    }
}

@Preview(name = "商户 Top 5（亮）", showBackground = true)
@Composable
private fun TopMerchantsLightPreview() {
    MaterialTheme {
        TopMerchantsCard(
            listOf(
                MerchantSum("星巴克", 345_60L),
                MerchantSum("美团外卖", 289_00L),
                MerchantSum("滴滴出行", 120_50L),
            ),
        )
    }
}
