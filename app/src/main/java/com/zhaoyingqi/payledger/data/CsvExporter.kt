package com.zhaoyingqi.payledger.data

import android.content.Context
import android.net.Uri
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 导出结果（v07 8.3）：成功 / 用户取消（未选文件）/ 失败（带原因）。 */
sealed interface ExportResult {
    data object Success : ExportResult
    data object Cancelled : ExportResult
    data class Failure(val message: String) : ExportResult
}

/**
 * CSV 导出（v07 8.2/D8）：CreateDocument 取得 URI 后经 ContentResolver 写入，
 * 整个写入在 Dispatchers.IO。数据源 [TransactionDao.getAllBetween] 已排除已删除记录。
 */
class CsvExporter(private val context: Context, private val dao: TransactionDao) {

    /** 导出 [from, to) 区间内全部未删除记录为 CSV；用户取消（uri 为 null）不产生文件。 */
    suspend fun export(uri: Uri?, from: Long, to: Long): ExportResult {
        if (uri == null) return ExportResult.Cancelled
        return withContext(Dispatchers.IO) {
            runCatching {
                val records = dao.getAllBetween(from, to)
                val csv = CsvEncoder.encode(records, ZoneId.systemDefault())
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(csv.toByteArray(Charsets.UTF_8))
                } ?: error("无法打开导出文件")
            }.fold(
                onSuccess = { ExportResult.Success },
                onFailure = { ExportResult.Failure(it.message ?: "导出失败") },
            )
        }
    }

    companion object {
        /** 默认文件名：PayLedger-yyyyMMdd.csv（v07 D8）。 */
        fun defaultFileName(now: Long, zone: ZoneId = ZoneId.systemDefault()): String =
            "PayLedger-" +
                DateTimeFormatter.ofPattern("yyyyMMdd").withZone(zone).format(Instant.ofEpochMilli(now)) +
                ".csv"

        /** 导出时间范围：全历史。 */
        const val EXPORT_FROM = 0L
        const val EXPORT_TO = Long.MAX_VALUE
    }
}
