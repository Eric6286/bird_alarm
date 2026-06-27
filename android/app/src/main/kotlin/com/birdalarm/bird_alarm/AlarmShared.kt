package com.birdalarm.bird_alarm

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager

// 原生侧各组件（接收器 / 服务 / 播放器 / 主活动）共用的状态存储文件名。集中成常量，避免某处手抄
// 出错时静默读到一个空的 prefs，导致 ringing_asset / launch_alarm 等协调标志失灵、闹钟行为出错。
const val PREFS_NAME = "bird_alarm_native"

// Android 16 (API 36) 把常驻通知请求提级为 Live Update 用的 extra key。
// 用字符串字面量而非 compileSdk 36 才有的符号，低版本上自动忽略。
const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"

// 锁屏或息屏 → 拉起全屏响铃页；亮屏且已解锁 → 只用通知提醒。
// 这是「锁屏全屏响铃」的关键门控判断，集中一处，避免接收器与前台服务两份拷贝将来改岔。
fun shouldUseFullScreen(context: Context): Boolean {
    val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return keyguardManager.isKeyguardLocked || !powerManager.isInteractive
}

// 响铃时把主界面(MainActivity, 含 showWhenLocked)带到前台、由 Flutter 显示全屏响铃遮罩的 Intent。
// 这四个 flag 与 launch_alarm 是绕过 Android 14+ BAL 的关键，集中一处，避免某路径漏 flag 导致全屏弹不出来。
fun fullScreenAlarmIntent(context: Context): Intent {
    return Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        putExtra("launch_alarm", true)
    }
}

// 排 / 取消「闹钟广播」用的 PendingIntent（请求码区分多路：1001/1004 主闹钟、1005 贪睡）。
// 排程与取消必须用同一份配方（同 component、同 extras、同 flags）才能匹配上，集中一处，
// 避免各处手抄漂移导致 cancel 匹配不到、闹钟取消不掉。
fun alarmBroadcastPendingIntent(context: Context, requestCode: Int): PendingIntent {
    return PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(context, AlarmReceiver::class.java).putExtra("launch_alarm", true),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

// setAlarmClock 的 show-intent：点系统状态栏「下一个闹钟」芯片时打开本应用（仅查看入口，不带 launch_alarm）。
fun alarmShowIntent(context: Context): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
    return PendingIntent.getActivity(
        context, 1001, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

fun preAlarmPendingIntent(context: Context, triggerAtMillis: Long): PendingIntent {
    return PendingIntent.getBroadcast(
        context,
        AlarmReceiver.PRE_ALARM_REQUEST_CODE,
        Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_PRE_ALARM)
            .putExtra(AlarmReceiver.EXTRA_TRIGGER_AT, triggerAtMillis),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

// 响铃前 10 分钟安排倒计时通知（Live Update）；若已不足 10 分钟则立即显示。
fun schedulePreAlarmCountdown(context: Context, triggerAtMillis: Long) {
    val leadAt = triggerAtMillis - AlarmReceiver.PRE_ALARM_LEAD_MILLIS
    if (leadAt <= System.currentTimeMillis()) {
        AlarmReceiver.showCountdownNotification(context, triggerAtMillis)
        return
    }
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, leadAt, preAlarmPendingIntent(context, triggerAtMillis)
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP, leadAt, preAlarmPendingIntent(context, triggerAtMillis)
            )
        }
    } catch (_: Exception) {
    }
}

// 「已守护」常驻通知：普通低优先级通知（非前台服务），保留可见反馈但不把进程钉在前台。
fun showGuardNotification(context: Context) {
    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            MainActivity.GUARD_CHANNEL_ID, "鸟瘾闹钟守护", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "已设定的下一次鸟鸣闹钟提示"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }
    val contentIntent = PendingIntent.getActivity(
        context, MainActivity.GUARD_CONTENT_REQUEST_CODE,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val builder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, MainActivity.GUARD_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
    val notification = builder
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle("鸟瘾闹钟已启用")
        .setContentText("下一次鸟鸣闹钟已守护")
        .setCategory(Notification.CATEGORY_STATUS)
        .setPriority(Notification.PRIORITY_LOW)
        .setOngoing(true)
        .setAutoCancel(false)
        .setShowWhen(false)
        .setVisibility(Notification.VISIBILITY_PUBLIC)
        .setContentIntent(contentIntent)
        .build()
    notificationManager.notify(MainActivity.GUARD_NOTIFICATION_ID, notification)
}

// 在 triggerAtMillis 排一个完整闹钟：精确闹钟(setAlarmClock + setExactAndAllowWhileIdle) +
// 响铃前倒计时 + 已守护通知。MainActivity(Flutter 排程) 与 AlarmReceiver(关闭倒计时后续排下一次) 共用。
fun armAlarmAt(context: Context, triggerAtMillis: Long) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val info = AlarmManager.AlarmClockInfo(triggerAtMillis, alarmShowIntent(context))
    alarmManager.setAlarmClock(info, alarmBroadcastPendingIntent(context, 1001))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, triggerAtMillis, alarmBroadcastPendingIntent(context, 1004)
        )
    } else {
        alarmManager.setExact(
            AlarmManager.RTC_WAKEUP, triggerAtMillis, alarmBroadcastPendingIntent(context, 1004)
        )
    }
    schedulePreAlarmCountdown(context, triggerAtMillis)
    showGuardNotification(context)
}

// Flutter 下发的「接下来若干次」发生时刻（逗号分隔的毫秒）。原生据此在每次响铃后/关闭后推进、续排下一次，
// 这样相近的多个闹钟也能一个接一个自动排上，不依赖打开 App。Flutter 每次同步会刷新整张表。
fun saveUpcomingTriggers(context: Context, csv: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putString("upcoming_triggers", csv).apply()
}

fun readUpcomingTriggers(context: Context): List<Long> {
    val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString("upcoming_triggers", null)
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split(",").mapNotNull { it.trim().toLongOrNull() }.sorted()
}

// 排掉「截止 throughMillis（含）之前」的所有发生，把剩下最早且晚于现在的那一次完整排上。
// 当前这次响铃后调用(throughMillis=now)、或在通知点关闭后调用(throughMillis=被关那次的时刻)。
fun armNextUpcoming(context: Context, throughMillis: Long) {
    val now = System.currentTimeMillis()
    val remaining = readUpcomingTriggers(context).filter { it > throughMillis }
    saveUpcomingTriggers(context, remaining.joinToString(","))
    val soonest = remaining.firstOrNull { it > now }
    if (soonest != null) armAlarmAt(context, soonest)
}
