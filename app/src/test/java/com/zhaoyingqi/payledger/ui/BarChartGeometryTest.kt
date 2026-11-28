package com.zhaoyingqi.payledger.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** v08 任务 2.2：柱状图点击命中——首列、末列、间隙处、含 padding。 */
class BarChartGeometryTest {

    // 360px 宽、6px 间距：barWidth = (360 - 6*11) / 12 = 24.5
    private val width = 360f
    private val gap = 6f
    private val slot = 24.5f + gap // 30.5

    @Test
    fun first_bar_at_left_edge() {
        assertEquals(0, BarChartGeometry.indexAt(0f, width, gap))
        assertEquals(0, BarChartGeometry.indexAt(24f, width, gap))
    }

    @Test
    fun last_bar_at_right_edge() {
        assertEquals(11, BarChartGeometry.indexAt(width - 1f, width, gap))
        // 第 12 根柱区间 [11*30.5, 360)
        assertEquals(11, BarChartGeometry.indexAt(11 * slot + 5f, width, gap))
    }

    @Test
    fun middle_bar() {
        // 第 6 根（索引 5）区间 [5*30.5, 5*30.5+24.5)
        assertEquals(5, BarChartGeometry.indexAt(5 * slot + 10f, width, gap))
    }

    @Test
    fun gap_taps_assign_to_left_bar() {
        // 间隙 [24.5, 30.5) 归索引 0
        assertEquals(0, BarChartGeometry.indexAt(27f, width, gap))
    }

    @Test
    fun padding_is_subtracted() {
        // paddingStart=20：x=25 相当于无 padding 时的 x=5 → 索引 0
        assertEquals(0, BarChartGeometry.indexAt(25f, width, gap, paddingStartPx = 20f))
        // paddingEnd=20：可用宽度 320，末列仍可命中
        assertEquals(11, BarChartGeometry.indexAt(319f, width, gap, paddingEndPx = 20f))
    }

    @Test
    fun invalid_layout_returns_minus_one() {
        assertEquals(-1, BarChartGeometry.indexAt(0f, 0f, gap))
        assertEquals(-1, BarChartGeometry.indexAt(0f, 10f, gap, paddingStartPx = 20f, paddingEndPx = 20f))
    }
}
