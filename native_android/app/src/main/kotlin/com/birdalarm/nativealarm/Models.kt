package com.birdalarm.nativealarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

enum class AlarmMode { NORMAL, BIRD_CHALLENGE }

data class BirdSound(
    val id: String,
    val cnName: String,
    val enName: String,
    val sciName: String,
    val assetPath: String,
)

data class BirdAlarm(
    val id: String,
    val hour: Int,
    val minute: Int,
    val repeatDays: Set<Int>,
    val mode: AlarmMode,
    val enabled: Boolean,
    val label: String,
) {
    fun nextTriggerMillis(after: LocalDateTime = LocalDateTime.now()): Long? {
        for (offset in 0..7) {
            val day = after.toLocalDate().plusDays(offset.toLong())
            if (repeatDays.isNotEmpty() && !repeatDays.contains(day.dayOfWeek.value)) continue
            val candidate = LocalDateTime.of(day, LocalTime.of(hour, minute))
            if (candidate.isAfter(after)) {
                return candidate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
        }
        return null
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("hour", hour)
        .put("minute", minute)
        .put("repeatDays", JSONArray(repeatDays.sorted()))
        .put("mode", mode.name)
        .put("enabled", enabled)
        .put("label", label)

    companion object {
        fun create(
            hour: Int,
            minute: Int,
            repeatDays: Set<Int> = setOf(1, 2, 3, 4, 5),
            label: String = "鸟鸣唤醒",
        ): BirdAlarm = BirdAlarm(
            id = UUID.randomUUID().toString(),
            hour = hour,
            minute = minute,
            repeatDays = repeatDays,
            mode = AlarmMode.BIRD_CHALLENGE,
            enabled = true,
            label = label,
        )

        fun fromJson(json: JSONObject): BirdAlarm = BirdAlarm(
            id = json.getString("id"),
            hour = json.optInt("hour", 7),
            minute = json.optInt("minute", 30),
            repeatDays = buildSet {
                val arr = json.optJSONArray("repeatDays") ?: JSONArray()
                for (i in 0 until arr.length()) add(arr.getInt(i))
            },
            mode = if (json.optString("mode") == AlarmMode.BIRD_CHALLENGE.name) {
                AlarmMode.BIRD_CHALLENGE
            } else {
                AlarmMode.NORMAL
            },
            enabled = json.optBoolean("enabled", true),
            label = json.optString("label", "鸟鸣唤醒"),
        )
    }
}

object BirdRepository {
    val builtIns = listOf(
        BirdSound("cuculus_micropterus", "四声杜鹃", "Indian Cuckoo", "Cuculus micropterus", "sounds/cuculus_micropterus.m4a"),
        BirdSound("cuculus_canorus", "大杜鹃", "Common Cuckoo", "Cuculus canorus", "sounds/cuculus_canorus.m4a"),
        BirdSound("spilornis_cheela", "蛇雕", "Crested Serpent Eagle", "Spilornis cheela", "sounds/spilornis_cheela.m4a"),
        BirdSound("francolinus_pintadeanus", "中华鹧鸪", "Chinese Francolin", "Francolinus pintadeanus", "sounds/francolinus_pintadeanus.m4a"),
        BirdSound("horornis_fortipes", "强脚树莺", "Brown-flanked Bush Warbler", "Horornis fortipes", "sounds/horornis_fortipes.m4a"),
        BirdSound("horornis_canturians", "远东树莺", "Manchurian Bush Warbler", "Horornis canturians", "sounds/horornis_canturians.m4a"),
        BirdSound("parus_cinereus", "大山雀", "Cinereous Tit", "Parus cinereus", "sounds/parus_cinereus.m4a"),
        BirdSound("dacelo_novaeguineae", "笑翠鸟", "Laughing Kookaburra", "Dacelo novaeguineae", "sounds/dacelo_novaeguineae.m4a"),
        BirdSound("psophodes_olivaceus", "绿啸冠鸫", "Eastern Whipbird", "Psophodes olivaceus", "sounds/psophodes_olivaceus.m4a"),
        BirdSound("eudynamys_scolopaceus", "噪鹃", "Asian Koel", "Eudynamys scolopaceus", "sounds/eudynamys_scolopaceus.m4a"),
    )

    fun byAsset(assetPath: String?): BirdSound =
        builtIns.firstOrNull { it.assetPath == assetPath } ?: builtIns.random()
}

class AlarmStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("alarms", Context.MODE_PRIVATE)

    fun load(): List<BirdAlarm> {
        val raw = prefs.getString("items", null) ?: return emptyList()
        val arr = JSONArray(raw)
        return List(arr.length()) { BirdAlarm.fromJson(arr.getJSONObject(it)) }
    }

    fun save(alarms: List<BirdAlarm>) {
        val arr = JSONArray()
        alarms.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("items", arr.toString()).apply()
    }

    fun upsert(alarm: BirdAlarm) {
        val next = load().filterNot { it.id == alarm.id } + alarm
        save(next)
    }

    fun delete(id: String) {
        save(load().filterNot { it.id == id })
    }

    fun byId(id: String?): BirdAlarm? = load().firstOrNull { it.id == id }

    fun nextEnabled(): Pair<BirdAlarm, Long>? =
        load().filter { it.enabled }
            .mapNotNull { alarm -> alarm.nextTriggerMillis()?.let { alarm to it } }
            .minByOrNull { it.second }
}
