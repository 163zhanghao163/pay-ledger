package com.zhaoyingqi.payledger.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v07 任务 1.3：首次建库必须预置内置分类规则。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CategoryRuleDaoTest {
    private lateinit var db: LedgerDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java)
            .allowMainThreadQueries()
            // 生产库经 LedgerDatabase.build() 已挂载预置回调；测试的 inMemory 库需显式挂载
            .addCallback(LedgerDatabase.PRESET_CALLBACK)
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun fresh_database_has_preset_rules() = runTest {
        val dao = db.categoryRuleDao()
        assertTrue("首次建库应预置规则", dao.count() >= CategoryRule.PRESETS.size)
        val rules = dao.getAll()
        // 每个预置关键词都存在且启用
        CategoryRule.PRESETS.forEach { (keyword, category, priority) ->
            val rule = rules.firstOrNull { it.keyword == keyword }
            assertTrue("预置关键词缺失: $keyword", rule != null)
            assertEquals(category, rule!!.category)
            assertEquals(priority, rule.priority)
            assertTrue(rule.enabled)
        }
    }

    @Test
    fun insert_conflict_on_keyword_ignored() = runTest {
        val dao = db.categoryRuleDao()
        val before = dao.count()
        val id = dao.insert(CategoryRule(keyword = "星巴克", category = "餐饮", priority = 10))
        assertEquals(-1L, id) // keyword 唯一索引冲突（预置已存在）
        assertEquals(before, dao.count())
    }

    @Test
    fun update_and_delete_rule() = runTest {
        val dao = db.categoryRuleDao()
        val id = dao.insert(CategoryRule(keyword = "自定义关键词", category = "其他", priority = 5))
        assertTrue(id > 0)
        dao.update(CategoryRule(id = id, keyword = "自定义关键词", category = "娱乐", priority = 8, enabled = false))
        val updated = dao.getAll().first { it.id == id }
        assertEquals("娱乐", updated.category)
        assertEquals(8, updated.priority)
        assertFalse(updated.enabled)

        dao.delete(id)
        assertNull(dao.getAll().firstOrNull { it.id == id })
        assertTrue(dao.observeAll().first().isNotEmpty())
    }
}
