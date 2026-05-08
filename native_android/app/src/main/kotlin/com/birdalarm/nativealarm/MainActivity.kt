package com.birdalarm.nativealarm

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDateTime

class MainActivity : Activity() {
    private lateinit var store: AlarmStore
    private lateinit var list: LinearLayout
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = AlarmStore(this)
        requestPermissionsForAlarm()
        render()
    }

    override fun onResume() {
        super.onResume()
        AlarmScheduler.scheduleNext(this)
        refreshList()
    }

    private fun render() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0xFFFFF5DF.toInt())
        }
        root.addView(TextView(this).apply {
            text = "鸟瘾闹钟"
            textSize = 30f
            setTextColor(0xFF164A45.toInt())
        })
        root.addView(Button(this).apply {
            text = "新建闹钟"
            setOnClickListener { createAlarm() }
        })
        root.addView(Button(this).apply {
            text = "权限检查"
            setOnClickListener { renderPermissionGuide() }
        })
        root.addView(Button(this).apply {
            text = "10 秒系统闹钟测试"
            setOnClickListener {
                val at = LocalDateTime.now().plusMinutes(1)
                val test = BirdAlarm.create(at.hour, at.minute, repeatDays = emptySet(), label = "10 秒测试")
                store.upsert(test)
                AlarmScheduler.scheduleAlarm(this@MainActivity, test.id, System.currentTimeMillis() + 10_000)
                refreshList()
                Toast.makeText(this@MainActivity, "已安排 10 秒系统闹钟测试，请息屏等待", Toast.LENGTH_LONG).show()
            }
        })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        refreshList()
    }

    private fun renderPermissionGuide() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0xFFFFF5DF.toInt())
        }
        page.addView(TextView(this).apply {
            text = "权限检查"
            textSize = 30f
            setTextColor(0xFF164A45.toInt())
        })
        page.addView(TextView(this).apply {
            text = "闹钟要在息屏时可靠响起，需要下面几项都放行。前四项可以检测，厂商后台/锁屏权限需要进入系统设置确认。"
            textSize = 15f
            setPadding(0, 8, 0, 18)
        })
        page.addView(permissionRow(
            title = "精确闹钟",
            ok = canScheduleExactAlarm(),
            action = "打开",
        ) { openExactAlarmSettings() })
        page.addView(permissionRow(
            title = "通知权限",
            ok = hasNotificationPermission(),
            action = "打开",
        ) { openAppNotificationSettings() })
        page.addView(permissionRow(
            title = "全屏闹钟通知",
            ok = canUseFullScreenIntent(),
            action = "打开",
        ) { openFullScreenIntentSettings() })
        page.addView(permissionRow(
            title = "电池无限制",
            ok = isIgnoringBatteryOptimizations(),
            action = "打开",
        ) { openBatteryOptimizationSettings() })
        page.addView(permissionRow(
            title = "后台启动 / 自启动",
            ok = null,
            action = "去设置",
        ) { openVendorPermissionSettings() })
        page.addView(permissionRow(
            title = "锁屏显示 / 后台弹出界面",
            ok = null,
            action = "去设置",
        ) { openVendorPermissionSettings() })
        page.addView(Button(this).apply {
            text = "返回"
            setOnClickListener { render() }
        })
        setContentView(ScrollView(this).apply { addView(page) })
    }

    private fun permissionRow(title: String, ok: Boolean?, action: String, onClick: () -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 12, 0, 12)
            addView(TextView(this@MainActivity).apply {
                text = "$title：${when (ok) {
                    true -> "已开启"
                    false -> "未开启"
                    null -> "请手动确认"
                }}"
                textSize = 20f
            })
            addView(Button(this@MainActivity).apply {
                text = action
                setOnClickListener { onClick() }
            })
        }

    private fun createAlarm() {
        val now = LocalDateTime.now().plusMinutes(1)
        TimePickerDialog(this, { _, hour, minute ->
            val alarm = BirdAlarm.create(hour, minute)
            store.upsert(alarm)
            val triggerAt = alarm.nextTriggerMillis()
            if (triggerAt != null) {
                AlarmScheduler.scheduleAlarm(this, alarm.id, triggerAt)
                Toast.makeText(this, "已守护 %02d:%02d 的闹钟".format(hour, minute), Toast.LENGTH_LONG).show()
            } else {
                AlarmScheduler.scheduleNext(this)
            }
            refreshList()
        }, now.hour, now.minute, true).show()
    }

    private fun refreshList() {
        if (!::list.isInitialized) return
        list.removeAllViews()
        val alarms = store.load()
        if (alarms.isEmpty()) {
            list.addView(TextView(this).apply { text = "还没有闹钟"; textSize = 18f })
        }
        alarms.sortedWith(compareBy({ it.hour }, { it.minute })).forEach { alarm ->
            list.addView(alarmRow(alarm))
        }
    }

    private fun alarmRow(alarm: BirdAlarm): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 18, 0, 18)
            addView(TextView(this@MainActivity).apply {
                text = "%02d:%02d  %s".format(alarm.hour, alarm.minute, alarm.label)
                textSize = 26f
            })
            addView(TextView(this@MainActivity).apply {
                text = if (alarm.mode == AlarmMode.BIRD_CHALLENGE) "强制认鸟" else "普通模式"
                textSize = 15f
            })
            addView(LinearLayout(this@MainActivity).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(Switch(this@MainActivity).apply {
                    text = "启用"
                    isChecked = alarm.enabled
                    setOnCheckedChangeListener { _, checked ->
                        store.upsert(alarm.copy(enabled = checked))
                        AlarmScheduler.scheduleNext(this@MainActivity)
                        refreshList()
                    }
                })
                addView(Button(this@MainActivity).apply {
                    text = if (alarm.mode == AlarmMode.BIRD_CHALLENGE) "改普通" else "改强制"
                    setOnClickListener {
                        val mode = if (alarm.mode == AlarmMode.BIRD_CHALLENGE) AlarmMode.NORMAL else AlarmMode.BIRD_CHALLENGE
                        store.upsert(alarm.copy(mode = mode))
                        AlarmScheduler.scheduleNext(this@MainActivity)
                        refreshList()
                    }
                })
                addView(Button(this@MainActivity).apply {
                    text = "删除"
                    setOnClickListener {
                        store.delete(alarm.id)
                        AlarmScheduler.scheduleNext(this@MainActivity)
                        refreshList()
                    }
                })
            })
        }

    private fun requestPermissionsForAlarm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
                    return
                } catch (_: Exception) {
                }
            }
        }
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            } catch (_: Exception) {
            }
        }
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun canScheduleExactAlarm(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            (getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()

    private fun canUseFullScreenIntent(): Boolean =
        Build.VERSION.SDK_INT < 34 ||
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).canUseFullScreenIntent()

    private fun isIgnoringBatteryOptimizations(): Boolean =
        (getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            openIntent(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        } else {
            openAppSettings()
        }
    }

    private fun openAppNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        }
        openIntent(intent)
    }

    private fun openFullScreenIntentSettings() {
        if (Build.VERSION.SDK_INT >= 34) {
            openIntent(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
        } else {
            openAppNotificationSettings()
        }
    }

    private fun openBatteryOptimizationSettings() {
        openFirstAvailable(
            listOf(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                Intent().setComponent(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"))
                    .putExtra("package_name", packageName)
                    .putExtra("package_label", "鸟瘾闹钟"),
                Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings")),
                Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
                Intent().setComponent(ComponentName("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity")),
                Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.privacypermissionsentry.PermissionTopActivity")),
                Intent().setComponent(ComponentName("com.oplus.battery", "com.oplus.powermanager.fuelgaue.PowerConsumptionActivity")),
                Intent().setComponent(ComponentName("com.vivo.abe", "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity")),
                Intent().setComponent(ComponentName("com.iqoo.powersaving", "com.iqoo.powersaving.PowerSavingManagerActivity")),
                Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
            ),
            "没有找到电池设置入口，请在系统设置里手动把鸟瘾闹钟设为电池无限制"
        )
    }

    private fun openVendorPermissionSettings() {
        val candidates = listOf(
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")).putExtra("extra_pkgname", packageName),
            Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
            Intent().setComponent(ComponentName("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity")),
            Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
            Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
        )
        openFirstAvailable(candidates, "没有找到厂商权限入口，请在应用详情里手动打开自启动和锁屏显示")
    }

    private fun openAppSettings() {
        openFirstAvailable(
            listOf(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))),
            "没有找到应用详情页"
        )
    }

    private fun openIntent(intent: Intent) {
        openFirstAvailable(
            listOf(intent, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))),
            "没有找到对应设置入口"
        )
    }

    private fun openFirstAvailable(intents: List<Intent>, failureMessage: String) {
        val opened = intents.any { intent ->
            try {
                startActivity(intent)
                true
            } catch (_: ActivityNotFoundException) {
                false
            } catch (_: Exception) {
                false
            }
        }
        if (!opened) Toast.makeText(this, failureMessage, Toast.LENGTH_LONG).show()
    }
}
