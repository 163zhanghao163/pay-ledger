package com.zhaoyingqi.payledger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert
    suspend fun insert(record: TransactionRecord): Long

    /** 按月查询列表（历史月份可回看），LIMIT 500：单月数据量小，MVP 不做分页。 */
    @Query("SELECT * FROM transactions WHERE occurredAt >= :startInclusive AND occurredAt < :endExclusive ORDER BY occurredAt DESC LIMIT 500")
    fun observeBetween(startInclusive: Long, endExclusive: Long): Flow<List<TransactionRecord>>

    /** 今日/本月汇总：按 [startInclusive, endExclusive) 区间求和。 */
    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM transactions WHERE occurredAt >= :startInclusive AND occurredAt < :endExclusive")
    fun observeSumBetween(startInclusive: Long, endExclusive: Long): Flow<Long>

    /** 防重复：同渠道同金额、时间窗内已有记录返回其 id。 */
    @Query("SELECT id FROM transactions WHERE channel = :channel AND amountCents = :amountCents AND occurredAt BETWEEN :from AND :to LIMIT 1")
    suspend fun findDuplicate(channel: String, amountCents: Long, from: Long, to: Long): Long?

    @Query("UPDATE transactions SET note = :note WHERE id = :id")
    suspend fun updateNote(id: Long, note: String)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: Long)
}
