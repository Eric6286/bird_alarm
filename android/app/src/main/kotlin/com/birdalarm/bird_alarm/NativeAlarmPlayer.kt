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
            ?: run {
                // 从 Flutter 下发的完整音库（内置 asset + 下载到本机的鸟鸣）里随机选；
                // 为空时回退内置 10 个。下载的鸟鸣由此真正进入闹钟的随机抽取池。
                val pool = prefs.getString("sound_pool", null)
                    ?.split('\n')
                    ?.filter { it.isNotBlank() }
                    ?.takeIf { it.isNotEmpty() }
                    ?: BirdAlarmAssets.sounds
                pool.random().also {
                    prefs.edit().putString("ringing_asset", it).apply()
                }
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
                val localFile = File(assetPath)
                if (assetPath.startsWith("/") && localFile.exists()) {
                    // 下载到本机的鸟鸣是普通文件，按文件路径直接播放。
                    setDataSource(localFile.absolutePath)
                } else {
                    val descriptor = appContext.assets.openFd(assetPath)
                    setDataSource(
                        descriptor.fileDescriptor,
                        descriptor.startOffset,
                        descriptor.length
                    )
                    descriptor.close()
                }
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
