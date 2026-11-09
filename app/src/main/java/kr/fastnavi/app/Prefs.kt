package kr.fastnavi.app

import android.content.Context

data class Settings(
    val tmapKey: String = "",
    val naverId: String = "",
    val naverSecret: String = "",
    val kakaoKey: String = "",
    val compareFree: Boolean = true,
    val autoLaunch: Boolean = false,
    val criterion: Criterion = Criterion.DEFAULT,
    val mode: Mode = Mode.SCREEN,
) {
    fun hasKey(app: NavApp) = when (app) {
        NavApp.TMAP -> tmapKey.isNotBlank()
        NavApp.NAVER -> naverId.isNotBlank() && naverSecret.isNotBlank()
        NavApp.KAKAO -> kakaoKey.isNotBlank()
    }
}

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("fastnavi", Context.MODE_PRIVATE)

    fun load() = Settings(
        tmapKey = sp.getString("tmapKey", "").orEmpty(),
        naverId = sp.getString("naverId", "").orEmpty(),
        naverSecret = sp.getString("naverSecret", "").orEmpty(),
        kakaoKey = sp.getString("kakaoKey", "").orEmpty(),
        compareFree = sp.getBoolean("compareFree", true),
        autoLaunch = sp.getBoolean("autoLaunch", false),
        criterion = runCatching { Criterion.valueOf(sp.getString("criterion", "DEFAULT")!!) }
            .getOrDefault(Criterion.DEFAULT),
        mode = runCatching { Mode.valueOf(sp.getString("mode", "SCREEN")!!) }.getOrDefault(Mode.SCREEN),
    )

    fun save(s: Settings) {
        sp.edit()
            .putString("tmapKey", s.tmapKey.trim())
            .putString("naverId", s.naverId.trim())
            .putString("naverSecret", s.naverSecret.trim())
            .putString("kakaoKey", s.kakaoKey.trim())
            .putBoolean("compareFree", s.compareFree)
            .putBoolean("autoLaunch", s.autoLaunch)
            .putString("criterion", s.criterion.name)
            .putString("mode", s.mode.name)
            .apply()
    }

    // 최근 목적지 (최대 5개)
    fun recentGoals(): List<Place> =
        sp.getString("recent", "").orEmpty().split("\n").filter { it.isNotBlank() }.mapNotNull { line ->
            val p = line.split("\t")
            if (p.size < 4) null
            else Place(p[0], p[1], p[2].toDoubleOrNull() ?: return@mapNotNull null,
                p[3].toDoubleOrNull() ?: return@mapNotNull null)
        }

    fun addRecentGoal(place: Place) {
        val list = (listOf(place) + recentGoals().filterNot { it.name == place.name && it.address == place.address })
            .take(5)
        val text = list.joinToString("\n") {
            listOf(it.name.replace("\t", " "), it.address.replace("\t", " "), it.lat, it.lng).joinToString("\t")
        }
        sp.edit().putString("recent", text).apply()
    }
}
