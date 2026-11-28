package com.zhaoyingqi.payledger.budget

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.zhaoyingqi.payledger.R
import com.zhaoyingqi.payledger.ui.formatCents

/**
 * 预算本地提醒（v08 4.3/D5）：月档位与当日额度共用一个渠道；
 * 权限不足时静默跳过（状态已由调用方用 commit 落盘，不会因权限反复触发）。
 */
object BudgetAlertNotifier {
    const val CHANNEL_ID = "budget"
    private const val ID_MONTH = 2001
    private const val ID_DAILY = 2002

    /** 渠道在首次启用预算时创建（D5），重复调用幂等。 */
    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager?.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, "预算提醒", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "达到每日或每月预算时提醒" }
        manager?.createNotificationChannel(channel)
    }

    /** 应用级通知是否可用（API 33+ 未授权 POST_NOTIFICATIONS 或被用户整体关闭都返回 false）。 */
    fun canNotify(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun notifyMonth(context: Context, level: Int, spentCents: Long, budgetCents: Long) {
        if (level == MonthlyAlertDecision.LEVEL_100) {
            post(
                context, ID_MONTH,
                title = "本月预算已用完",
                body = "本月已支出 ${formatCents(spentCents)}，达到月预算 ${formatCents(budgetCents)}",
            )
        } else {
            post(
                context, ID_MONTH,
                title = "本月预算提醒",
                body = "本月已支出 ${formatCents(spentCents)}，达到月预算 ${formatCents(budgetCents)} 的 80%",
            )
        }
    }

    fun notifyDaily(context: Context, todaySpentCents: Long, dailyCents: Long) {
        post(
            context, ID_DAILY,
            title = "今日额度提醒",
            body = "今天已支出 ${formatCents(todaySpentCents)}，达到每日预算 ${formatCents(dailyCents)}",
        )
    }

    // canNotify() 已同时覆盖 API 33+ 的 POST_NOTIFICATIONS 授权与用户整体关闭通知两种情况，lint 无法跨方法识别
    @SuppressLint("MissingPermission")
    private fun post(context: Context, id: Int, title: String, body: String) {
        if (!canNotify(context)) return
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        val pending = PendingIntent.getActivity(
            context, id, launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_budget)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }
}
