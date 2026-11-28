package com.zhaoyingqi.payledger.ui

/**
 * 12 根月度柱的点击命中几何（v08 2.2/D6，纯函数）：扣除 paddingStart 与柱间距后按等分命中。
 * 间隙处归左列；x 超出右端归末列；宽度非法返回 -1。
 */
object BarChartGeometry {
    const val BAR_COUNT = 12

    fun indexAt(
        xPx: Float,
        widthPx: Float,
        gapPx: Float,
        paddingStartPx: Float = 0f,
        paddingEndPx: Float = 0f,
    ): Int {
        val usable = widthPx - paddingStartPx - paddingEndPx
        if (usable <= 0f || gapPx < 0f) return -1
        val barWidth = (usable - gapPx * (BAR_COUNT - 1)) / BAR_COUNT
        if (barWidth <= 0f) return -1
        val index = ((xPx - paddingStartPx) / (barWidth + gapPx)).toInt()
        return index.coerceIn(0, BAR_COUNT - 1)
    }
}
