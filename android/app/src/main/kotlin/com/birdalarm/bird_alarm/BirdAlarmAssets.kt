package com.birdalarm.bird_alarm

import android.content.Context

object BirdAlarmAssets {
    val sounds = listOf(
        "flutter_assets/assets/sounds/cuculus_micropterus.m4a",
        "flutter_assets/assets/sounds/cuculus_canorus.m4a",
        "flutter_assets/assets/sounds/spilornis_cheela.m4a",
        "flutter_assets/assets/sounds/francolinus_pintadeanus.m4a",
        "flutter_assets/assets/sounds/horornis_fortipes.m4a",
        "flutter_assets/assets/sounds/horornis_canturians.m4a",
        "flutter_assets/assets/sounds/parus_cinereus.m4a",
        "flutter_assets/assets/sounds/dacelo_novaeguineae.m4a",
        "flutter_assets/assets/sounds/psophodes_olivaceus.m4a",
        "flutter_assets/assets/sounds/eudynamys_scolopaceus.m4a"
    )

    // 内置 asset 文件名 → 中文鸟名，与 Flutter 侧 _starterLibrary 保持一致。
    // 内置鸟鸣的名字查这里；下载到本机的鸟鸣由 Flutter 通过 sound_names 映射下发，
    // 见 cnNameFor()——所以响铃通知对下载的鸟鸣也能显示正确中文名。
    private val cnNames = mapOf(
        "cuculus_micropterus.m4a" to "四声杜鹃",
        "cuculus_canorus.m4a" to "大杜鹃",
        "spilornis_cheela.m4a" to "蛇雕",
        "francolinus_pintadeanus.m4a" to "中华鹧鸪",
        "horornis_fortipes.m4a" to "强脚树莺",
        "horornis_canturians.m4a" to "远东树莺",
        "parus_cinereus.m4a" to "大山雀",
        "dacelo_novaeguineae.m4a" to "笑翠鸟",
        "psophodes_olivaceus.m4a" to "绿啸冠鸫",
        "eudynamys_scolopaceus.m4a" to "噪鹃"
    )

    fun cnNameFor(context: Context, assetPath: String?): String {
        if (assetPath.isNullOrEmpty()) return "鸟鸣"
        // 下载的鸟鸣文件名不在内置映射里，先查 Flutter 下发的"路径→中文名"映射。
        try {
            val raw = context
                .getSharedPreferences("bird_alarm_native", Context.MODE_PRIVATE)
                .getString("sound_names", null)
            if (!raw.isNullOrEmpty()) {
                val name = org.json.JSONObject(raw).optString(assetPath)
                if (name.isNotEmpty()) return name
            }
        } catch (_: Exception) {
        }
        val fileName = assetPath.substringAfterLast('/')
        return cnNames[fileName] ?: "鸟鸣"
    }
}
