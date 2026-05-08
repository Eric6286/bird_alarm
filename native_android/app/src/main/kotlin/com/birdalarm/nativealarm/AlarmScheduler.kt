package com.birdalarm.nativealarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object AlarmScheduler {
    fun scheduleNext(context: Context) {
        val next = AlarmStore(context).nextEnabled()
        if (next == null) {
            cancel(context)
            stopRinging(context)
            return
        }
        val (alarm, triggerAtMillis) = next
        scheduleAlarm(context, alarm.id, triggerAtMillis)
    }

    fun scheduleAlarm(context: Context, alarmId: String, triggerAtMillis: Long) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val operation = receiverIntent(context, 2001, alarmId)
        val showIntent = PendingIntent.getActivity(
            context,
            2002,
            Intent(context, AlarmRingActivity::class.java)
                .putExtra("alarm_id", alarmId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent), operation)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, receiverIntent(context, 2003, alarmId))
        } else {
            manager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, receiverIntent(context, 2003, alarmId))
        }
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.cancel(receiverIntent(context, 2001, ""))
        manager.cancel(receiverIntent(context, 2003, ""))
    }

    fun ringNow(context: Context, alarmId: String? = null) {
        val intent = Intent(context, AlarmGuardService::class.java)
            .setAction(AlarmGuardService.ACTION_RING)
            .putExtra(AlarmGuardService.EXTRA_ALARM_ID, alarmId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
    }

    fun stopRinging(context: Context) {
        context.stopService(Intent(context, AlarmGuardService::class.java))
    }

    private fun receiverIntent(context: Context, requestCode: Int, alarmId: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, AlarmReceiver::class.java).putExtra("alarm_id", alarmId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
