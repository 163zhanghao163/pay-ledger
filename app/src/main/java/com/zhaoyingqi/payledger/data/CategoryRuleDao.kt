package com.zhaoyingqi.payledger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** 分类规则增删改查（v07 任务 1.1）。keyword 唯一，冲突时忽略。 */
@Dao
interface CategoryRuleDao {
    /** 供归类与规则页使用：按优先级降序、禁用的排在最后（CategoryMatcher 自己也会过滤）。 */
    @Query("SELECT * FROM category_rules ORDER BY priority DESC, keyword")
    fun observeAll(): Flow<List<CategoryRule>>

    @Query("SELECT * FROM category_rules ORDER BY priority DESC, keyword")
    suspend fun getAll(): List<CategoryRule>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(rule: CategoryRule): Long

    @Update
    suspend fun update(rule: CategoryRule)

    @Query("DELETE FROM category_rules WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM category_rules")
    suspend fun count(): Int
}
