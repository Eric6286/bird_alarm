package com.birdalarm.nativealarm

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class AlarmRingActivity : Activity() {
    private lateinit var alarm: BirdAlarm
    private lateinit var sound: BirdSound

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prepareWindow()
        val prefs = getSharedPreferences("ringing", Context.MODE_PRIVATE)
        alarm = AlarmStore(this).byId(prefs.getString("alarm_id", null))
            ?: AlarmStore(this).nextEnabled()?.first
            ?: BirdAlarm.create(7, 30)
        sound = BirdRepository.byAsset(prefs.getString("asset_path", null))
        if (!NativeAlarmPlayer.isPlaying()) NativeAlarmPlayer.start(this, sound.assetPath)
        render()
    }

    private fun prepareWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.rgb(255, 245, 223))
        }
        root.addView(TextView(this).apply {
            text = "鸟瘾闹钟"
            textSize = 32f
            setTextColor(Color.rgb(22, 74, 69))
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = if (alarm.mode == AlarmMode.BIRD_CHALLENGE) "强制认鸟模式：答对才能关闭" else "普通模式：随机鸟鸣正在响起"
            textSize = 18f
            setPadding(0, 18, 0, 28)
            gravity = Gravity.CENTER
        })
        if (alarm.mode == AlarmMode.BIRD_CHALLENGE) {
            val options = (listOf(sound) + BirdRepository.builtIns.filter { it.id != sound.id }.shuffled().take(3)).shuffled()
            options.forEach { option ->
                root.addView(Button(this).apply {
                    text = option.cnName
                    textSize = 18f
                    setOnClickListener {
                        if (option.id == sound.id) dismiss()
                    }
                })
            }
        } else {
            root.addView(Button(this).apply {
                text = "关闭闹钟"
                textSize = 18f
                setOnClickListener { dismiss() }
            })
        }
        setContentView(root)
    }

    private fun dismiss() {
        NativeAlarmPlayer.stop()
        AlarmScheduler.stopRinging(this)
        AlarmScheduler.scheduleNext(this)
        finish()
    }
}
