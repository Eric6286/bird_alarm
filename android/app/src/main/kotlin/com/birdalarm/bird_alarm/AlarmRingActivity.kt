package com.birdalarm.bird_alarm

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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prepareLockscreenWindow()
        showAlarmUi()
        NativeAlarmPlayer.start(this)
    }

    private fun prepareLockscreenWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )
    }

    private fun showAlarmUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(40, 40, 40, 40)
            setBackgroundColor(Color.rgb(255, 245, 223))
        }
        val title = TextView(this).apply {
            text = "鸟瘾闹钟"
            textSize = 32f
            setTextColor(Color.rgb(22, 74, 69))
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "随机鸟鸣正在响起"
            textSize = 18f
            setTextColor(Color.rgb(60, 51, 36))
            gravity = Gravity.CENTER
            setPadding(0, 18, 0, 34)
        }
        val button = Button(this).apply {
            text = "进入强制清醒挑战"
            textSize = 18f
            setOnClickListener {
                startActivity(
                    Intent(this@AlarmRingActivity, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("launch_alarm", true)
                    }
                )
                finish()
            }
        }
        root.addView(title)
        root.addView(subtitle)
        root.addView(button)
        setContentView(root)
    }
}
