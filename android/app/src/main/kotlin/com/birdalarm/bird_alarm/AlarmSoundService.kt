package com.birdalarm.bird_alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager

class AlarmSoundService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var armedRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_ARM -> {
                val triggerAtMillis = intent.getLongExtra(EXTRA_TRIGGER_AT_MILLIS, 0L)
                arm(triggerAtMillis)
                return START_STICKY
            }
            ACTION_RING -> {
                ring()
                return START_STICKY
            }
        }
        startForeground(NOTIFICATION_ID, buildNotification(isRinging = false))
        return START_STICKY
    }

    override fun onDestroy() {
        armedRunnable?.let { handler.removeCallbacks(it) }
        armedRunnable = null
        NativeAlarmPlayer.stop(this)
        super.onDestroy()
    }

    private fun arm(triggerAtMillis: Long) {
        startForeground(NOTIFICATION_ID, buildNotification(isRinging = false, triggerAtMillis = triggerAtMillis))
        armedRunnable?.let { handler.removeCallbacks(it) }
        val delay = (triggerAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        armedRunnable = Runnable { ring() }
        handler.postDelayed(armedRunnable!!, delay)
    }

    private fun ring() {
        armedRunnable?.let { handler.removeCallbacks(it) }
        armedRunnable = null
        val prefs = getSharedPreferences("bird_alarm_native", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastTrigger = prefs.getLong("last_trigger_at", 0L)
        if (now - lastTrigger < 3_000 && NativeAlarmPlayer.isPlaying()) return
        prefs.edit()
            .putBoolean("launch_alarm", true)
            .putLong("last_trigger_at", now)
            .apply()

        val wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "bird_alarm:service_wake"
            )
        @Suppress("DEPRECATION")
        wakeLock.acquire(30_000)
        startForeground(NOTIFICATION_ID, buildNotification(isRinging = true))
        NativeAlarmPlayer.start(this)
        try {
            startActivity(
                Intent(this, AlarmRingActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    putExtra("launch_alarm", true)
                }
            )
        } catch (_: Exception) {
        }
    }

    private fun buildNotification(
        isRinging: Boolean,
        triggerAtMillis: Long = 0L,
    ): Notification {
        val channelId = CHANNEL_ID
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "鸟瘾闹钟响铃",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "闹钟响铃和强制清醒挑战"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        val contentActivity =
            if (isRinging) AlarmRingActivity::class.java else MainActivity::class.java
        val activityIntent = Intent(this, contentActivity).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            if (isRinging) putExtra("launch_alarm", true)
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            1002,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1003,
            Intent(this, AlarmSoundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, channelId)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }
        val contentText =
            if (isRinging) {
                "随机鸟鸣正在响起，点这里进入强制清醒挑战"
            } else {
                "下一次鸟鸣闹钟已守护"
            }
        val title = if (isRinging) "鸟瘾闹钟" else "鸟瘾闹钟已启用"
        return builder
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(contentText)
            .setTicker(if (isRinging) "鸟瘾闹钟正在响铃" else "鸟瘾闹钟已启用")
            .setCategory(if (isRinging) Notification.CATEGORY_ALARM else Notification.CATEGORY_STATUS)
            .setPriority(if (isRinging) Notification.PRIORITY_MAX else Notification.PRIORITY_LOW)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setWhen(if (triggerAtMillis > 0) triggerAtMillis else System.currentTimeMillis())
            .setShowWhen(true)
            .apply {
                if (isRinging) setFullScreenIntent(contentIntent, true)
            }
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止", stopIntent)
            .build()
    }

    companion object {
        const val ACTION_ARM = "com.birdalarm.bird_alarm.ARM_ALARM_SOUND"
        const val ACTION_RING = "com.birdalarm.bird_alarm.RING_ALARM_SOUND"
        const val ACTION_STOP = "com.birdalarm.bird_alarm.STOP_ALARM_SOUND"
        const val EXTRA_TRIGGER_AT_MILLIS = "trigger_at_millis"
        const val CHANNEL_ID = "bird_alarm_ringing"
        const val NOTIFICATION_ID = 1001
    }
}
