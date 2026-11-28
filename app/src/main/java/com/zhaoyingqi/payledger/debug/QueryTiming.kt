package com.zhaoyingqi.payledger.debug

import android.os.SystemClock
import android.util.Log
import com.zhaoyingqi.payledger.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart

private const val TAG = "PerfTiming"

/**
 * v09 D4 性能埋点：debug 构建打印 flow 从订阅到每次发射的耗时与结果规模，
 * release 原样透传（R8 可消除）。只输出查询标签与行数，不输出任何账目内容。
 *
 * total=订阅到本次发射的累计耗时（首发射即查询耗时）；sinceLast=相邻两次发射间隔。
 */
fun <T> Flow<T>.timed(tag: String, sizeOf: (T) -> Int = { 0 }): Flow<T> {
    if (!BuildConfig.DEBUG) return this
    var subscribedAt = 0L
    var lastAt = 0L
    return onStart {
        subscribedAt = SystemClock.elapsedRealtime()
        lastAt = subscribedAt
    }.onEach { value ->
        val now = SystemClock.elapsedRealtime()
        Log.d(TAG, "$tag total=${now - subscribedAt}ms sinceLast=${now - lastAt}ms rows=${sizeOf(value)}")
        lastAt = now
    }
}
