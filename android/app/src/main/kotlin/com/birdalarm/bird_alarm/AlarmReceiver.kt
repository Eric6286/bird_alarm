package com.birdalarm.bird_alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.app.Notification

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        cancelThisAlarmRound(context)
        val prefs = context.getSharedPreferences("bird_alarm_native", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastTrigger = prefs.getLong("last_trigger_at", 0L)
        if (now - lastTrigger < 30_000) return

        prefs
            .edit()
            .putBoolean("launch_alarm", true)
            .putLong("last_trigger_at", now)
            .apply()

        val wakeLock = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "bird_alarm:alarm_wake"
            )
        @Suppress("DEPRECATION")
        wakeLock.acquire(30_000)

        NativeAlarmPlayer.start(context)

        try {
            val soundIntent = Intent(context, AlarmSoundService::class.java)
                .setAction(AlarmSoundService.ACTION_RING)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(soundIntent)
            } else {
                context.startService(soundIntent)
            }
        } catch (_: Exception) {
            // The receiver-level player already started as the fallback alarm.
        }

        val activityIntent = Intent(context, AlarmRingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra("launch_alarm", true)
        }
        showFullScreenNotification(context, activityIntent)
        try {
            context.startActivity(activityIntent)
        } catch (_: Exception) {
            // The full-screen alarm notification remains as the fallback entry point.
        }
    }

    private fun cancelThisAlarmRound(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        listOf(1001, 1004).forEach { requestCode ->
            alarmManager.cancel(
                PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    Intent(context, AlarmReceiver::class.java).putExtra("launch_alarm", true),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }
        alarmManager.cancel(
            PendingIntent.getActivity(
                context,
                1001,
                Intent(context, AlarmRingActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    putExtra("launch_alarm", true)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    private fun showFullScreenNotification(context: Context, activityIntent: Intent) {
        val channelId = "bird_alarm_ringing"
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "鸟瘾闹钟响铃",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "闹钟响铃和强制清醒挑战"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        val fullScreenIntent = PendingIntent.getActivity(
            context,
            1002,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, channelId)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context)
            }
        val notification = builder
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("鸟瘾闹钟")
            .setContentText("随机鸟鸣正在响起，点这里进入强制清醒挑战")
            .setTicker("鸟瘾闹钟正在响铃")
            .setCategory(Notification.CATEGORY_ALARM)
            .setPriority(Notification.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenIntent, true)
            .setContentIntent(fullScreenIntent)
            .build()

        notificationManager.notify(1001, notification)
    }
}
