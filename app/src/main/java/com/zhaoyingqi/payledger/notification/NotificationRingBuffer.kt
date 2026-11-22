package com.zhaoyingqi.payledger.notification

/** 原始捕获样本，仅 debug 预览页使用；source 标记来源通道（通知 / 无障碍）。 */
data class RawNotification(
    val packageName: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val source: String = "通知",
)

/** 固定容量环形缓冲，线程安全。仅内存不落盘；容量 30 且只保留最近 7 天，重启即清空。 */
class NotificationRingBuffer(private val capacity: Int = 30) {
    private val buf = ArrayDeque<RawNotification>(capacity)

    @Synchronized
    fun push(item: RawNotification) {
        if (buf.size >= capacity) buf.removeFirst()
        buf.addLast(item)
    }

    /** 只保留最近 [RETENTION_MS] 内的样本（超期条目顺带清掉）。 */
    @Synchronized
    fun snapshot(): List<RawNotification> {
        val threshold = System.currentTimeMillis() - RETENTION_MS
        while (buf.isNotEmpty() && buf.first().postedAt < threshold) buf.removeFirst()
        return buf.filter { it.postedAt >= threshold }
    }

    @Synchronized
    fun clear() = buf.clear()

    companion object { const val RETENTION_MS = 7 * 24 * 60 * 60 * 1000L }
}
