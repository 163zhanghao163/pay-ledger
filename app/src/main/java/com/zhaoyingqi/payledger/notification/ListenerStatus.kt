package com.zhaoyingqi.payledger.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 监听状态快照（v08 D1）：三路来源合并，UI 单一数据源。 */
data class ListenerSnapshot(
    val authorized: Boolean,
    val notificationConnected: Boolean,
    val accessibilityConnected: Boolean,
)

/**
 * 单一监听状态流：通知监听服务、无障碍服务与授权刷新处各写一路，
 * UI `collectAsStateWithLifecycle` 订阅，事件驱动、不再轮询。
 */
object ListenerStatus {
    private val _snapshot = MutableStateFlow(ListenerSnapshot(false, false, false))
    val snapshot: StateFlow<ListenerSnapshot> = _snapshot.asStateFlow()

    fun setAuthorized(authorized: Boolean) {
        _snapshot.value = _snapshot.value.copy(authorized = authorized)
    }

    fun setNotificationConnected(connected: Boolean) {
        _snapshot.value = _snapshot.value.copy(notificationConnected = connected)
    }

    fun setAccessibilityConnected(connected: Boolean) {
        _snapshot.value = _snapshot.value.copy(accessibilityConnected = connected)
    }

    /** 授权状态每次 ON_RESUME 都会重查（D2），测试与刷新入口可整体覆盖。 */
    fun replace(authorized: Boolean, notificationConnected: Boolean, accessibilityConnected: Boolean) {
        _snapshot.value = ListenerSnapshot(authorized, notificationConnected, accessibilityConnected)
    }
}
