package com.zhaoyingqi.payledger.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** v07 任务 5.1：归类匹配纯函数——命中、未命中、多条命中取高优先级、禁用规则不参与。 */
class CategoryMatcherTest {

    private fun rule(keyword: String, category: String, priority: Int = 10, enabled: Boolean = true) =
        CategoryRule(keyword = keyword, category = category, priority = priority, enabled = enabled)

    @Test
    fun match_returns_category_on_hit() {
        val category = CategoryMatcher.match("星巴克咖啡", listOf(rule("星巴克", "餐饮")))
        assertEquals("餐饮", category)
    }

    @Test
    fun no_hit_returns_null() {
        assertNull(CategoryMatcher.match("某便利店", listOf(rule("星巴克", "餐饮"))))
    }

    @Test
    fun blank_counterparty_returns_null() {
        assertNull(CategoryMatcher.match("", listOf(rule("星巴克", "餐饮"))))
        assertNull(CategoryMatcher.match("   ", listOf(rule("星巴克", "餐饮"))))
    }

    @Test
    fun multiple_hits_take_highest_priority() {
        val rules = listOf(
            rule("美团", "餐饮", priority = 10),
            rule("美团打车", "交通", priority = 20),
        )
        // 「美团打车」比「美团」更具体：priority 20 先命中
        assertEquals("交通", CategoryMatcher.match("美团打车", rules))
        assertEquals("餐饮", CategoryMatcher.match("美团外卖", rules))
    }

    @Test
    fun disabled_rules_do_not_participate() {
        val rules = listOf(
            rule("星巴克", "餐饮", enabled = false),
            rule("星巴克甄选", "购物", enabled = true),
        )
        // 禁用的「星巴克」不参与：只按启用的「星巴克甄选」判定，未包含则不归类
        assertNull(CategoryMatcher.match("星巴克", rules))
        assertEquals("购物", CategoryMatcher.match("星巴克甄选门店", rules))
    }

    @Test
    fun empty_rules_returns_null() {
        assertNull(CategoryMatcher.match("星巴克", emptyList()))
    }
}
