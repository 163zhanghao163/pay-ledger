package com.zhaoyingqi.payledger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** 统计页用的月度合计行。 */
data class MonthSum(val month: Int, val totalCents: Long)

/** 分类占比聚合行（v07 D7）。 */
data class CategorySum(val category: String, val totalCents: Long)

/** 商户排行聚合行（v07 D7）。 */
data class MerchantSum(val counterparty: String, val totalCents: Long)

@Dao
interface TransactionDao {
    companion object {
        /**
         * 「未删除」过滤（v07 D1）：集中定义，列表/汇总/统计/搜索/导出查询一律引用。
         * 唯一例外是判重候选查询——不过滤已删除记录，用户删掉的付款不会被再次采集回来。
         */
        const val NOT_DELETED = "deletedAt IS NULL"

        /** 搜索结果上限（v07 D2）：超过则配合 [countSearch] 提示缩小范围。 */
        const val SEARCH_LIMIT = 500
    }

    /** dedupKey 唯一索引兜底：冲突时返回 -1，保证极端并发下同一笔只落一条。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: TransactionRecord): Long

    /** 按月查询列表（历史月份可回看）。单月数据量可控，不再限制条数。 */
    @Query(
        "SELECT * FROM transactions WHERE $NOT_DELETED AND occurredAt >= :startInclusive AND occurredAt < :endExclusive " +
            "ORDER BY occurredAt DESC",
    )
    fun observeBetween(startInclusive: Long, endExclusive: Long): Flow<List<TransactionRecord>>

    /** 今日/本月支出汇总：按 [startInclusive, endExclusive) 区间求和。 */
    @Query(
        "SELECT COALESCE(SUM(amountCents), 0) FROM transactions WHERE $NOT_DELETED AND type = 'EXPENSE' " +
            "AND occurredAt >= :startInclusive AND occurredAt < :endExclusive",
    )
    fun observeSumBetween(startInclusive: Long, endExclusive: Long): Flow<Long>

    /** 收入汇总（v07 6.3）。 */
    @Query(
        "SELECT COALESCE(SUM(amountCents), 0) FROM transactions WHERE $NOT_DELETED AND type = 'INCOME' " +
            "AND occurredAt >= :startInclusive AND occurredAt < :endExclusive",
    )
    fun observeIncomeSumBetween(startInclusive: Long, endExclusive: Long): Flow<Long>

    /** 是否存在任何未删除记录（空态引导判断，v08 3.3）。 */
    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE $NOT_DELETED)")
    fun observeAnyRecord(): Flow<Boolean>

    /**
     * 判重候选：同渠道同金额、采集时间在窗口内，返回整行交 DuplicatePolicy 判定。
     * 注意：不过滤已删除记录（v07 D1 例外）。
     */
    @Query(
        "SELECT * FROM transactions WHERE channel = :channel AND amountCents = :amountCents " +
            "AND capturedAt BETWEEN :from AND :to",
    )
    suspend fun findDuplicateCandidates(channel: String, amountCents: Long, from: Long, to: Long): List<TransactionRecord>

    /** 某年各月支出合计（统计页柱状图）。 */
    @Query(
        "SELECT CAST(strftime('%m', occurredAt/1000, 'unixepoch', 'localtime') AS INTEGER) AS month, " +
            "SUM(amountCents) AS totalCents FROM transactions WHERE $NOT_DELETED AND type = 'EXPENSE' " +
            "AND occurredAt >= :startInclusive AND occurredAt < :endExclusive GROUP BY month ORDER BY month",
    )
    fun observeMonthlySums(startInclusive: Long, endExclusive: Long): Flow<List<MonthSum>>

    /** 分类占比（v07 D7）：区间内按类型聚合，金额降序。 */
    @Query(
        "SELECT category, SUM(amountCents) AS totalCents FROM transactions WHERE $NOT_DELETED AND type = :type " +
            "AND category IS NOT NULL AND occurredAt >= :startInclusive AND occurredAt < :endExclusive " +
            "GROUP BY category ORDER BY totalCents DESC",
    )
    fun observeCategorySums(type: String, startInclusive: Long, endExclusive: Long): Flow<List<CategorySum>>

    /** 商户 Top 5（v07 D7）：区间内按商户聚合，金额降序。 */
    @Query(
        "SELECT counterparty, SUM(amountCents) AS totalCents FROM transactions WHERE $NOT_DELETED AND type = :type " +
            "AND counterparty != '' AND occurredAt >= :startInclusive AND occurredAt < :endExclusive " +
            "GROUP BY counterparty ORDER BY totalCents DESC LIMIT 5",
    )
    fun observeTopMerchants(type: String, startInclusive: Long, endExclusive: Long): Flow<List<MerchantSum>>

    /** 编辑记录：金额、备注与发生时间一起更新；用户确认的时间不再视为估算。 */
    @Query(
        "UPDATE transactions SET amountCents = :amountCents, note = :note, occurredAt = :occurredAt, " +
            "timeEstimated = 0 WHERE id = :id",
    )
    suspend fun update(id: Long, amountCents: Long, note: String, occurredAt: Long)

    /** 编辑扩展（v07 3.1）：渠道/商户/类型/分类一起改；source 与 dedupKey 不在更新列，结构上保证不被改。 */
    @Query(
        "UPDATE transactions SET amountCents = :amountCents, note = :note, occurredAt = :occurredAt, " +
            "channel = :channel, counterparty = :counterparty, type = :type, category = :category, " +
            "timeEstimated = 0 WHERE id = :id",
    )
    suspend fun updateFull(
        id: Long,
        amountCents: Long,
        note: String,
        occurredAt: Long,
        channel: String,
        counterparty: String,
        type: String,
        category: String?,
    )

    /** 软删除（v07 D3）：置 deletedAt，撤销即恢复为 NULL。返回受影响行数（0 表示已删除）。 */
    @Query("UPDATE transactions SET deletedAt = :now WHERE id = :id AND deletedAt IS NULL")
    suspend fun softDelete(id: Long, now: Long): Int

    @Query("UPDATE transactions SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: Long)

    /** 过期清理（v07 4.3）：物理删除软删除超过期限的记录。返回清理条数。 */
    @Query("DELETE FROM transactions WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun purgeDeleted(before: Long): Int

    /**
     * 搜索（v07 D2）：可空参数组合单条 SQL，关键词 LIKE 转义后模糊匹配商户与备注。
     * 上限 500 条，配合 [countSearch] 提示缩小范围。
     */
    @Query(
        "SELECT * FROM transactions WHERE $NOT_DELETED " +
            "AND occurredAt >= :from AND occurredAt < :to " +
            "AND (:channel IS NULL OR channel = :channel) " +
            "AND (:type IS NULL OR type = :type) " +
            "AND (:category IS NULL OR category = :category) " +
            "AND (:minCents IS NULL OR amountCents >= :minCents) " +
            "AND (:maxCents IS NULL OR amountCents <= :maxCents) " +
            "AND (:keyword IS NULL OR counterparty LIKE '%' || :keyword || '%' ESCAPE '\\' " +
            "OR note LIKE '%' || :keyword || '%' ESCAPE '\\') " +
            "ORDER BY occurredAt DESC LIMIT $SEARCH_LIMIT",
    )
    fun search(
        from: Long,
        to: Long,
        channel: String?,
        type: String?,
        category: String?,
        minCents: Long?,
        maxCents: Long?,
        keyword: String?,
    ): Flow<List<TransactionRecord>>

    /** 与 [search] 相同条件的总数（判断是否超 500 条）。 */
    @Query(
        "SELECT COUNT(*) FROM transactions WHERE $NOT_DELETED " +
            "AND occurredAt >= :from AND occurredAt < :to " +
            "AND (:channel IS NULL OR channel = :channel) " +
            "AND (:type IS NULL OR type = :type) " +
            "AND (:category IS NULL OR category = :category) " +
            "AND (:minCents IS NULL OR amountCents >= :minCents) " +
            "AND (:maxCents IS NULL OR amountCents <= :maxCents) " +
            "AND (:keyword IS NULL OR counterparty LIKE '%' || :keyword || '%' ESCAPE '\\' " +
            "OR note LIKE '%' || :keyword || '%' ESCAPE '\\')",
    )
    suspend fun countSearch(
        from: Long,
        to: Long,
        channel: String?,
        type: String?,
        category: String?,
        minCents: Long?,
        maxCents: Long?,
        keyword: String?,
    ): Int

    /** 导出数据源（v07 8.2）：区间内全部未删除记录（含收入），按时间正序导出。 */
    @Query(
        "SELECT * FROM transactions WHERE $NOT_DELETED AND occurredAt >= :startInclusive AND occurredAt < :endExclusive " +
            "ORDER BY occurredAt ASC",
    )
    suspend fun getAllBetween(startInclusive: Long, endExclusive: Long): List<TransactionRecord>

    /** 一次性清理旧版本写入的通知原文摘要，只保留结构化摘要。 */
    @Query(
        "UPDATE transactions SET summary = CASE channel " +
            "WHEN 'WECHAT' THEN '微信付款' WHEN 'ALIPAY' THEN '支付宝付款' ELSE summary END " +
            "WHERE summary != '' AND summary != '手动记账' AND summary NOT IN ('微信付款', '支付宝付款')",
    )
    suspend fun scrubLegacySummaries()
}
