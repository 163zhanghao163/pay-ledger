package com.zhaoyingqi.payledger.data

/**
 * 搜索关键词预处理（v07 D2，纯函数）：转义 LIKE 通配符与转义符本身，
 * 与 DAO 的 `LIKE ... ESCAPE '\'` 配套，保证 `%`/`_` 按字面匹配而不是通配符。
 */
object SearchQuery {
    fun build(raw: String): String? =
        raw.trim()
            .takeIf { it.isNotEmpty() }
            ?.replace("\\", "\\\\")
            ?.replace("%", "\\%")
            ?.replace("_", "\\_")
}
