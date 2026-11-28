package com.zhaoyingqi.payledger.data

/**
 * 归类匹配（v07 D5，纯函数）：商户名包含关键词即命中，按 priority 降序取第一条；
 * 禁用规则不参与。只在入账（自动/手动）时调用一次，规则修改不追溯。
 */
object CategoryMatcher {
    fun match(counterparty: String, rules: List<CategoryRule>): String? {
        if (counterparty.isBlank()) return null
        return rules
            .asSequence()
            .filter { it.enabled }
            .sortedByDescending { it.priority }
            .firstOrNull { counterparty.contains(it.keyword) }
            ?.category
    }
}
