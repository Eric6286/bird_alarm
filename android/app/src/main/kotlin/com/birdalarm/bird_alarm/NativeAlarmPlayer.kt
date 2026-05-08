package com.birdalarm.bird_alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import java.io.File

object NativeAlarmPlayer {
    private var player: MediaPlayer? = null

    fun isPlaying(): Boolean = player?.isPlaying == true

    fun start(context: Context) {
        if (player?.isPlaying == true) return
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences("bird_alarm_native", Context.MODE_PRIVATE)
        val assetPath = prefs.getString("ringing_asset", null)
            ?: BirdAlarmAssets.sounds.random().also {
                prefs.edit().putString("ringing_asset", it).apply()
            }

        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        @Suppress("DEPRECATION")
        audioManager.requestAudioFocus(
            null,
            AudioManager.STREAM_ALARM,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
        )

        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            isLooping = true
            try {
                val descriptor = appContext.assets.openFd(assetPath)
                setDataSource(
                    descriptor.fileDescriptor,
                    descriptor.startOffset,
                    descriptor.length
                )
                descriptor.close()
            } catch (_: Exception) {
                try {
                    val file = File(appContext.cacheDir, assetPath.substringAfterLast('/'))
                    appContext.assets.open(assetPath).use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    setDataSource(file.absolutePath)
                } catch (_: Exception) {
                    val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    setDataSource(appContext, uri)
                }
            }
            setVolume(1f, 1f)
            prepare()
            start()
        }
    }

    fun stop(context: Context) {
        player?.run {
            try {
                stop()
            } catch (_: Exception) {
            }
            release()
        }
        player = null
        context.applicationContext
            .getSharedPreferences("bird_alarm_native", Context.MODE_PRIVATE)
            .edit()
            .remove("ringing_asset")
            .apply()
    }
}
