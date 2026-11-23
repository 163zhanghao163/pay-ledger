package com.zhaoyingqi.payledger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** 统计页用的月度合计行。 */
data class MonthSum(val month: Int, val totalCents: Long)

@Dao
interface TransactionDao {
    @Insert
    suspend fun insert(record: TransactionRecord): Long

    /** 按月查询列表（历史月份可回看）。单月数据量可控，不再限制 500 条，避免较早记录不可见。 */
    @Query("SELECT * FROM transactions WHERE occurredAt >= :startInclusive AND occurredAt < :endExclusive ORDER BY occurredAt DESC")
    fun observeBetween(startInclusive: Long, endExclusive: Long): Flow<List<TransactionRecord>>

    /** 今日/本月汇总：按 [startInclusive, endExclusive) 区间求和。 */
    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM transactions WHERE occurredAt >= :startInclusive AND occurredAt < :endExclusive")
    fun observeSumBetween(startInclusive: Long, endExclusive: Long): Flow<Long>

    /** 防重复：同渠道同金额、时间窗内已有记录返回其 id。 */
    @Query("SELECT id FROM transactions WHERE channel = :channel AND amountCents = :amountCents AND occurredAt BETWEEN :from AND :to LIMIT 1")
    suspend fun findDuplicate(channel: String, amountCents: Long, from: Long, to: Long): Long?

    /** 某年各月支出合计（统计页柱状图）。 */
    @Query("SELECT CAST(strftime('%m', occurredAt/1000, 'unixepoch', 'localtime') AS INTEGER) AS month, SUM(amountCents) AS totalCents FROM transactions WHERE occurredAt >= :startInclusive AND occurredAt < :endExclusive GROUP BY month ORDER BY month")
    fun observeMonthlySums(startInclusive: Long, endExclusive: Long): Flow<List<MonthSum>>

    /** 编辑记录：金额与备注一起更新。 */
    @Query("UPDATE transactions SET amountCents = :amountCents, note = :note WHERE id = :id")
    suspend fun update(id: Long, amountCents: Long, note: String)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: Long)

    /** 一次性清理旧版本写入的通知原文摘要，只保留结构化摘要。 */
    @Query(
        "UPDATE transactions SET summary = CASE channel " +
            "WHEN 'WECHAT' THEN '微信付款' WHEN 'ALIPAY' THEN '支付宝付款' ELSE summary END " +
            "WHERE summary != '' AND summary != '手动记账' AND summary NOT IN ('微信付款', '支付宝付款')",
    )
    suspend fun scrubLegacySummaries()
}
