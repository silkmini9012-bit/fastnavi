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

    // 최근 목적지: 고정(★)은 개수 제한 없이 맨 위, 나머지는 최근 순 최대 8개
    fun recentGoals(): List<Place> =
        sp.getString("recent", "").orEmpty().split("\n").filter { it.isNotBlank() }.mapNotNull { line ->
            val p = line.split("\t")
            if (p.size < 4) return@mapNotNull null
            Place(
                name = p[0], address = p[1],
                lat = p[2].toDoubleOrNull() ?: return@mapNotNull null,
                lng = p[3].toDoubleOrNull() ?: return@mapNotNull null,
                pinned = p.getOrNull(4) == "1",
            )
        }.sortedByDescending { it.pinned }

    private fun same(a: Place, b: Place) = a.name == b.name && a.address == b.address

    private fun saveList(list: List<Place>) {
        val pinned = list.filter { it.pinned }
        val others = list.filterNot { it.pinned }.take(8)
        val text = (pinned + others).joinToString("\n") {
            listOf(
                it.name.replace("\t", " ").replace("\n", " "),
                it.address.replace("\t", " ").replace("\n", " "),
                it.lat, it.lng, if (it.pinned) "1" else "0",
            ).joinToString("\t")
        }
        sp.edit().putString("recent", text).apply()
    }

    fun addRecentGoal(place: Place) {
        val old = recentGoals()
        val wasPinned = old.any { same(it, place) && it.pinned }
        saveList(listOf(place.copy(pinned = wasPinned, isCurrentLocation = false)) + old.filterNot { same(it, place) })
    }

    fun togglePin(place: Place) =
        saveList(recentGoals().map { if (same(it, place)) it.copy(pinned = !it.pinned) else it })

    fun removeRecent(place: Place) = saveList(recentGoals().filterNot { same(it, place) })
}
