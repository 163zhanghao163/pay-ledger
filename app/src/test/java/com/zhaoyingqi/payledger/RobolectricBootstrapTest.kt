package com.zhaoyingqi.payledger

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 验证 Robolectric 测试地基可用（v06 任务 1.1）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RobolectricBootstrapTest {
    @Test
    fun context_is_available() {
        val context: Context = ApplicationProvider.getApplicationContext()
        assertEquals("com.zhaoyingqi.payledger", context.packageName)
    }
}
