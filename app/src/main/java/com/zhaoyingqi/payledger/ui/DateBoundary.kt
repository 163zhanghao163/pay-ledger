package com.zhaoyingqi.payledger.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** 边界事件：当前本地日期 + 时区。午夜到点或时区变化时更新。 */
data class DateZone(val date: LocalDate, val zone: ZoneId)

/**
 * 日期边界流（v06 D8）：
 * - 发射当前本地日期与时区；
 * - 下一本地午夜到点时重新发射（时长按 [clock] 计算，测试可注入虚拟时钟）；
 * - 系统时区变化（ACTION_TIMEZONE_CHANGED 广播）立即重新发射；
 * - 广播接收器仅在存在订阅者时注册，收集结束即反注册；[context] 为 null（纯 JVM 测试）时不注册。
 */
internal fun dateBoundaryFlow(clock: Clock, context: Context? = null): Flow<DateZone> = channelFlow {
    fun current(): DateZone = DateZone(clock.instant().atZone(clock.zone).toLocalDate(), clock.zone)

    var receiver: BroadcastReceiver? = null
    if (context != null) {
        receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                trySend(current())
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_TIMEZONE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    var last = current()
    send(last)
    try {
        while (true) {
            val nowMs = clock.millis()
            val nextMidnight = clock.instant().atZone(clock.zone).toLocalDate().plusDays(1)
                .atStartOfDay(clock.zone).toInstant().toEpochMilli()
            delay((nextMidnight - nowMs).coerceAtLeast(1L))
            val now = current()
            if (now != last) {
                last = now
                send(now)
            }
        }
    } finally {
        receiver?.let { context?.unregisterReceiver(it) }
    }
}.distinctUntilChanged()
