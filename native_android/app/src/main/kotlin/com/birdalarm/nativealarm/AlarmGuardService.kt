package com.birdalarm.nativealarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager

class AlarmGuardService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RING -> {
                ring(intent.getStringExtra(EXTRA_ALARM_ID))
                return START_STICKY
            }
        }
        startForeground(NOTIFICATION_ID, buildNotification(false, 0L))
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        NativeAlarmPlayer.stop()
        super.onDestroy()
    }

    private fun ring(alarmId: String?) {
        val alarm = AlarmStore(this).byId(alarmId) ?: AlarmStore(this).nextEnabled()?.first
        val sound = BirdRepository.builtIns.random()
        getSharedPreferences("ringing", Context.MODE_PRIVATE)
            .edit()
            .putString("alarm_id", alarm?.id)
            .putString("asset_path", sound.assetPath)
            .apply()

        val wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "bird_alarm_native:ring"
            )
        @Suppress("DEPRECATION")
        wakeLock.acquire(30_000)

        startForeground(NOTIFICATION_ID, buildNotification(true, 0L))
        NativeAlarmPlayer.start(this, sound.assetPath)
        try {
            startActivity(
                Intent(this, AlarmRingActivity::class.java)
                    .putExtra("alarm_id", alarm?.id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        } catch (_: Exception) {
        }
    }

    private fun buildNotification(isRinging: Boolean, triggerAt: Long): Notification {
        ensureChannel()
        val activity = if (isRinging) AlarmRingActivity::class.java else MainActivity::class.java
        val contentIntent = PendingIntent.getActivity(
            this,
            3001,
            Intent(this, activity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            3002,
            Intent(this, AlarmGuardService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val text = if (isRinging) "随机鸟鸣正在响起，点这里进入挑战" else "下一次鸟鸣闹钟已守护"
        return builder
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(if (isRinging) "鸟瘾闹钟" else "鸟瘾闹钟已启用")
            .setContentText(text)
            .setCategory(if (isRinging) Notification.CATEGORY_ALARM else Notification.CATEGORY_STATUS)
            .setPriority(if (isRinging) Notification.PRIORITY_MAX else Notification.PRIORITY_LOW)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setWhen(if (triggerAt > 0) triggerAt else System.currentTimeMillis())
            .setShowWhen(true)
            .setContentIntent(contentIntent)
            .apply { if (isRinging) setFullScreenIntent(contentIntent, true) }
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止", stopIntent)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "鸟瘾闹钟", NotificationManager.IMPORTANCE_HIGH)
                .apply {
                    description = "鸟瘾闹钟响铃与守护服务"
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
        )
    }

    companion object {
        const val ACTION_RING = "com.birdalarm.nativealarm.RING"
        const val ACTION_STOP = "com.birdalarm.nativealarm.STOP"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val CHANNEL_ID = "bird_alarm_native"
        const val NOTIFICATION_ID = 4001
    }
}
