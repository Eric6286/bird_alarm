package com.birdalarm.bird_alarm

import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
