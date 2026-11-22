package com.zhaoyingqi.payledger.notification

/** 原始通知样本，仅 debug 预览页使用。 */
data class RawNotification(val packageName: String, val title: String, val text: String, val postedAt: Long)

/** 固定容量环形缓冲，线程安全。容量 30：微信聊天通知多，避免付款凭证被挤出。 */
class NotificationRingBuffer(private val capacity: Int = 30) {
    private val buf = ArrayDeque<RawNotification>(capacity)

    @Synchronized
    fun push(item: RawNotification) {
        if (buf.size >= capacity) buf.removeFirst()
        buf.addLast(item)
    }

    @Synchronized
    fun snapshot(): List<RawNotification> = buf.toList()
}
